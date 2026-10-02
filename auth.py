"""Student accounts: registration, login, sessions, profile (SQLite + hashed passwords)."""
import os, re, secrets, sqlite3, time
from functools import wraps
from flask import abort, flash, g, jsonify, redirect, render_template, request, session, url_for
from werkzeug.security import check_password_hash, generate_password_hash

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "instance")
DB_PATH = os.path.join(BASE, "skillbridge.db")
DEPARTMENTS = ["Computer Science", "Information Technology", "Electronics and Communication",
               "Electrical Engineering", "Mechanical Engineering", "Civil Engineering",
               "Business Administration", "Data Science", "Other"]
YEARS = [1, 2, 3, 4, 5, 6]
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")
FAILS = {}  # (email, ip) -> (failed attempts, window start)
DUMMY = generate_password_hash("not-a-real-password")  # keeps timing equal for unknown emails


def db():
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
    return g.db


def current_user():
    return g.user


def csrf_token():
    return session.setdefault("csrf", secrets.token_hex(16))


def login_required(f):
    @wraps(f)
    def wrapper(*a, **k):
        if not g.user:
            if request.path.startswith("/api/"):
                return jsonify(error="Your session ended. Please log in again."), 401
            return redirect(url_for("login"))
        return f(*a, **k)
    return wrapper


def clean_profile(f, careers):
    v = {"name": " ".join(f.get("name", "").split()), "department": f.get("department", ""),
         "year": f.get("year", ""), "career_goal": f.get("career_goal", "")}
    e = {}
    if not 2 <= len(v["name"]) <= 80: e["name"] = "Enter your full name (2 to 80 characters)."
    if v["department"] not in DEPARTMENTS: e["department"] = "Choose your department."
    if v["year"] not in [str(y) for y in YEARS]: e["year"] = "Choose your year of study."
    if v["career_goal"] not in careers: e["career_goal"] = "Choose a career goal."
    return v, e


def init_auth(app, careers):
    os.makedirs(BASE, exist_ok=True)
    key_file = os.path.join(BASE, "secret_key")
    if not os.path.exists(key_file):
        with open(key_file, "w") as fh:
            fh.write(secrets.token_hex(32))
        os.chmod(key_file, 0o600)
    app.config.update(
        SECRET_KEY=os.environ.get("SECRET_KEY") or open(key_file).read().strip(),
        SESSION_COOKIE_HTTPONLY=True, SESSION_COOKIE_SAMESITE="Lax",
        SESSION_COOKIE_SECURE=bool(os.environ.get("PRODUCTION")),  # set PRODUCTION=1 behind HTTPS
        PERMANENT_SESSION_LIFETIME=7200)
    con = sqlite3.connect(DB_PATH)
    con.execute("""CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT NOT NULL UNIQUE,
        password_hash TEXT NOT NULL, department TEXT NOT NULL, year INTEGER NOT NULL,
        career_goal TEXT NOT NULL, skills TEXT NOT NULL DEFAULT '',
        created_at TEXT DEFAULT CURRENT_TIMESTAMP)""")
    con.execute("""CREATE TABLE IF NOT EXISTS roadmap_progress (
        user_id INTEGER NOT NULL REFERENCES users(id),
        career TEXT NOT NULL, skill TEXT NOT NULL, completed INTEGER NOT NULL DEFAULT 0,
        updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (user_id, career, skill))""")
    con.execute("""CREATE TABLE IF NOT EXISTS saved_items (
        user_id INTEGER NOT NULL REFERENCES users(id),
        item_type TEXT NOT NULL, item_key TEXT NOT NULL, title TEXT NOT NULL,
        details TEXT NOT NULL DEFAULT '', created_at TEXT DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (user_id, item_type, item_key))""")
    con.commit(); con.close()

    @app.teardown_appcontext
    def close_db(_):
        d = g.pop("db", None)
        if d: d.close()

    @app.before_request
    def load_user_and_check_csrf():
        g.user = None
        if session.get("uid"):
            g.user = db().execute("SELECT * FROM users WHERE id=?", (session["uid"],)).fetchone()
            if not g.user: session.clear()
        if request.method == "POST":
            sent = request.form.get("csrf") or request.headers.get("X-CSRF-Token", "")
            tok = session.get("csrf", "")
            if not tok or not sent or not secrets.compare_digest(sent.encode(), tok.encode()):
                abort(400)

    @app.after_request
    def headers(r):
        r.headers["X-Content-Type-Options"] = "nosniff"
        r.headers["X-Frame-Options"] = "DENY"
        if g.get("user"): r.headers["Cache-Control"] = "no-store"
        return r

    @app.context_processor
    def inject():
        return {"csrf_token": csrf_token, "user": g.get("user")}

    @app.route("/register", methods=["GET", "POST"])
    def register():
        if g.user: return redirect(url_for("dashboard"))
        v, e = {}, {}
        if request.method == "POST":
            v, e = clean_profile(request.form, careers)
            v["email"] = request.form.get("email", "").strip().lower()
            pw, pw2 = request.form.get("password", ""), request.form.get("confirm", "")
            if not EMAIL_RE.match(v["email"]) or len(v["email"]) > 120: e["email"] = "Enter a valid email address."
            if not 8 <= len(pw) <= 128 or not re.search(r"[A-Za-z]", pw) or not re.search(r"\d", pw):
                e["password"] = "Use 8 or more characters with at least one letter and one number."
            elif pw != pw2: e["confirm"] = "The passwords do not match."
            if not e:
                try:
                    cur = db().execute(
                        "INSERT INTO users (name,email,password_hash,department,year,career_goal) VALUES (?,?,?,?,?,?)",
                        (v["name"], v["email"], generate_password_hash(pw), v["department"], int(v["year"]), v["career_goal"]))
                    db().commit()
                except sqlite3.IntegrityError:
                    e["email"] = "An account with this email already exists. Try logging in."
                else:
                    session.clear(); session.permanent = True; session["uid"] = cur.lastrowid
                    return redirect(url_for("dashboard"))
        return render_template("auth.html", mode="register", v=v, e=e, departments=DEPARTMENTS, years=YEARS, careers=careers)

    @app.route("/login", methods=["GET", "POST"])
    def login():
        if g.user: return redirect(url_for("dashboard"))
        e, email = {}, ""
        if request.method == "POST":
            email = request.form.get("email", "").strip().lower()
            key = (email, request.remote_addr)
            n, t = FAILS.get(key, (0, time.time()))
            if time.time() - t > 600: n, t = 0, time.time()
            if n >= 5:
                e["form"] = "Too many failed attempts. Please wait a few minutes and try again."
            else:
                row = db().execute("SELECT id, password_hash FROM users WHERE email=?", (email,)).fetchone()
                match = check_password_hash(row["password_hash"] if row else DUMMY, request.form.get("password", ""))
                if row and match:
                    FAILS.pop(key, None); session.clear(); session.permanent = True; session["uid"] = row["id"]
                    return redirect(url_for("dashboard"))
                FAILS[key] = (n + 1, t)
                e["form"] = "The email or password is incorrect."
        return render_template("auth.html", mode="login", v={"email": email}, e=e)

    @app.post("/logout")
    def logout():
        session.clear()
        return redirect(url_for("login"))

    @app.route("/profile", methods=["GET", "POST"])
    @login_required
    def profile():
        v, e = dict(g.user), {}
        if request.method == "POST":
            v, e = clean_profile(request.form, careers)
            if not e:
                db().execute("UPDATE users SET name=?, department=?, year=?, career_goal=? WHERE id=?",
                             (v["name"], v["department"], int(v["year"]), v["career_goal"], g.user["id"]))
                db().commit(); flash("Profile updated.")
                return redirect(url_for("profile"))
        return render_template("profile.html", v=v, e=e, departments=DEPARTMENTS, years=YEARS,
                               careers=careers, current_page="profile")
