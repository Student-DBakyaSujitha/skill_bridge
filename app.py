"""SkillBridge: compare a student's skills with a career's requirements.
Run:  pip install -r requirements.txt && python app.py
"""
import re
from difflib import get_close_matches
from flask import Flask, flash, jsonify, redirect, render_template, request, url_for
from werkzeug.exceptions import HTTPException
from auth import current_user, db, init_auth, login_required

app = Flask(__name__)

# level 1 = foundation, 2 = core, 3 = advanced
CAREERS = {
    "Web Developer": {
        "skills": {
            "html": (1, "Build 3 static pages using semantic tags."),
            "css": (1, "Recreate a site layout with flexbox and grid."),
            "javascript": (1, "Add DOM interactivity: forms, fetch, events."),
            "git": (1, "Commit, branch and open a pull request on GitHub."),
            "responsive design": (2, "Make one page work from 360px to 1440px."),
            "rest apis": (2, "Build and call a small JSON API."),
            "sql": (2, "Model two related tables and write joins."),
            "react": (2, "Rebuild a small page as components with state."),
            "testing": (3, "Write unit tests for one API route."),
            "deployment": (3, "Deploy an app and set environment variables."),
        },
        "projects": [
            ("Personal portfolio site", ["html", "css", "responsive design", "git"], "Show 3 projects with a contact form."),
            ("Expense tracker with an API", ["javascript", "rest apis", "sql"], "Add, edit and chart expenses stored in a database."),
            ("Deployed team task board", ["react", "testing", "deployment"], "Live URL with tests and a README."),
        ],
    },
    "Data Analyst": {
        "skills": {
            "excel": (1, "Practice pivot tables and lookups on a public dataset."),
            "sql": (1, "Answer 20 business questions with SELECT, JOIN, GROUP BY."),
            "statistics": (1, "Cover mean, variance, distributions and hypothesis tests."),
            "python": (2, "Write scripts that read, filter and summarize CSVs."),
            "pandas": (2, "Clean a messy dataset: nulls, types, duplicates."),
            "data visualization": (2, "Choose the right chart and label it clearly."),
            "tableau": (2, "Build one interactive dashboard with filters."),
            "a/b testing": (3, "Analyze a sample experiment and report significance."),
            "storytelling": (3, "Present findings in a 5-slide summary for non-experts."),
        },
        "projects": [
            ("Sales dashboard", ["sql", "excel", "tableau", "data visualization"], "Track revenue by region and month."),
            ("Public dataset cleanup and analysis", ["python", "pandas", "statistics"], "Publish a notebook with cleaning steps and insights."),
            ("Experiment readout", ["a/b testing", "storytelling"], "Report whether a change improved conversion."),
        ],
    },
    "Machine Learning Engineer": {
        "skills": {
            "python": (1, "Get fluent with functions, classes and virtual environments."),
            "statistics": (1, "Study probability, bias/variance and evaluation metrics."),
            "linear algebra": (1, "Review vectors, matrices and gradients."),
            "pandas": (2, "Prepare features from a raw dataset."),
            "sql": (2, "Pull training data with joins and aggregations."),
            "machine learning": (2, "Train and compare 3 models on one problem."),
            "scikit-learn": (2, "Use pipelines and cross-validation."),
            "deep learning": (3, "Train a small neural net and plot its loss."),
            "pytorch": (3, "Implement a training loop yourself."),
            "mlops": (3, "Track experiments and serve a model behind an API."),
        },
        "projects": [
            ("House price predictor", ["python", "pandas", "scikit-learn", "machine learning"], "Compare models and explain errors."),
            ("Image classifier", ["deep learning", "pytorch"], "Train on a small dataset and report accuracy."),
            ("Model served as an API", ["mlops", "python", "sql"], "Containerize a model with a prediction endpoint."),
        ],
    },
}

# Alternate spellings and related tools map onto canonical skill names.
ALIASES = {
    "js": "javascript", "ecmascript": "javascript", "node": "javascript",
    "html5": "html", "css3": "css", "flexbox": "css", "tailwind": "css", "bootstrap": "css",
    "github": "git", "version control": "git",
    "api": "rest apis", "apis": "rest apis", "flask": "rest apis", "fastapi": "rest apis", "express": "rest apis",
    "mysql": "sql", "postgres": "sql", "postgresql": "sql", "sqlite": "sql", "databases": "sql",
    "reactjs": "react", "react.js": "react",
    "pytest": "testing", "unit tests": "testing", "jest": "testing",
    "docker": "deployment", "heroku": "deployment", "aws": "deployment",
    "py": "python", "numpy": "python",
    "stats": "statistics", "probability": "statistics",
    "matplotlib": "data visualization", "seaborn": "data visualization", "power bi": "tableau", "charts": "data visualization",
    "ml": "machine learning", "sklearn": "scikit-learn",
    "tensorflow": "deep learning", "keras": "deep learning", "neural networks": "deep learning",
    "linear algebra": "linear algebra", "matrices": "linear algebra",
    "ab testing": "a/b testing", "presenting": "storytelling",
    "mobile first": "responsive design", "responsive": "responsive design",
}

ALL_SKILLS = {s for c in CAREERS.values() for s in c["skills"]}
VOCAB = {**{s: s for s in ALL_SKILLS}, **ALIASES}
SINGLE = [t for t in VOCAB if re.fullmatch(r"[a-z0-9.+#-]+", t)]


def extract_skills(text: str) -> set:
    """Lightweight NLP: normalize, match phrases/aliases, then fuzzy-match typos."""
    text = text.lower()
    found = set()
    for term, canon in VOCAB.items():
        if re.search(rf"(?<![\w+#.]){re.escape(term)}(?![\w+#])", text):
            found.add(canon)
    for tok in set(re.findall(r"[a-z0-9.+#-]{4,}", text)):
        if tok in VOCAB:
            continue
        hit = get_close_matches(tok, SINGLE, n=1, cutoff=0.85)
        if hit:
            found.add(VOCAB[hit[0]])
    return found


PHASES = {1: "Foundations", 2: "Core skills", 3: "Advanced and job-ready"}
LEVEL_NAMES = {1: "Beginner", 2: "Intermediate", 3: "Advanced"}
RESOURCE_LINKS = {
    "html": [("MDN HTML", "https://developer.mozilla.org/en-US/docs/Learn/HTML")],
    "css": [("MDN CSS", "https://developer.mozilla.org/en-US/docs/Learn/CSS")],
    "javascript": [("MDN JavaScript", "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide")],
    "git": [("GitHub Skills", "https://skills.github.com/")],
    "responsive design": [("web.dev responsive design", "https://web.dev/learn/design/")],
    "rest apis": [("MDN HTTP overview", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview")],
    "sql": [("SQLBolt", "https://sqlbolt.com/")],
    "react": [("React Learn", "https://react.dev/learn")],
    "testing": [("Testing Library", "https://testing-library.com/docs/")],
    "deployment": [("Render deployment guides", "https://render.com/docs/deploys")],
    "excel": [("Microsoft Excel help", "https://support.microsoft.com/excel")],
    "statistics": [("Khan Academy statistics", "https://www.khanacademy.org/math/statistics-probability")],
    "python": [("Python tutorial", "https://docs.python.org/3/tutorial/")],
    "pandas": [("Pandas getting started", "https://pandas.pydata.org/docs/getting_started/index.html")],
    "data visualization": [("Datawrapper Academy", "https://academy.datawrapper.de/")],
    "tableau": [("Tableau free training", "https://www.tableau.com/learn/training")],
    "a/b testing": [("Optimizely A/B testing guide", "https://www.optimizely.com/optimization-glossary/ab-testing/")],
    "storytelling": [("Storytelling with data", "https://www.storytellingwithdata.com/")],
    "linear algebra": [("Khan Academy linear algebra", "https://www.khanacademy.org/math/linear-algebra")],
    "machine learning": [("Google Machine Learning Crash Course", "https://developers.google.com/machine-learning/crash-course")],
    "scikit-learn": [("scikit-learn tutorials", "https://scikit-learn.org/stable/tutorial/index.html")],
    "deep learning": [("Dive into Deep Learning", "https://d2l.ai/")],
    "pytorch": [("PyTorch tutorials", "https://pytorch.org/tutorials/")],
    "mlops": [("Made With ML", "https://madewithml.com/")],
}


init_auth(app, list(CAREERS))


@app.route("/")
def home():
    have = extract_skills("HTML, CSS, JavaScript and Git")  # real sample run for the landing page
    chips = [{"name": n, "have": n in have} for n in CAREERS["Web Developer"]["skills"]]
    score = round(100 * sum(c["have"] for c in chips) / len(chips))
    cards = [{"name": n, "count": len(c["skills"]), "projects": len(c["projects"]), "top": list(c["skills"])[:4]}
             for n, c in CAREERS.items()]
    return render_template("landing.html", chips=chips, score=score, cards=cards)


@app.errorhandler(HTTPException)
def http_error(e):
    msg = {400: "That request could not be verified. Refresh the page and try again.",
           404: "We couldn't find that page."}.get(e.code, e.description)
    if request.path.startswith("/api/"):
        return jsonify(error=msg), e.code
    return render_template("error.html", code=e.code, msg=msg), e.code


@app.route("/dashboard")
@login_required
def dashboard():
    return render_student_page("dashboard")


def render_student_page(page):
    user = current_user()
    analysis = build_analysis(user["career_goal"], user["skills"], user["id"])
    saved = db().execute(
        "SELECT item_type, item_key, title, details FROM saved_items WHERE user_id=? ORDER BY created_at DESC",
        (user["id"],),
    ).fetchall()
    return render_template(
        "index.html", careers=list(CAREERS), page=page, analysis=analysis,
        saved_items=[dict(row) for row in saved],
    )


@app.route("/skills")
@login_required
def my_skills():
    return render_student_page("skills")


@app.route("/analysis")
@login_required
def skill_analysis():
    return render_student_page("analysis")


@app.route("/roadmap")
@login_required
def learning_roadmap():
    return render_student_page("roadmap")


@app.route("/projects")
@login_required
def project_recommendations():
    return render_student_page("projects")


@app.route("/resources")
@login_required
def learning_resources():
    return render_student_page("resources")


@app.route("/progress")
@login_required
def progress_tracker():
    return render_student_page("progress")


@app.route("/saved")
@login_required
def saved_items_page():
    return render_student_page("saved")


@app.route("/settings")
@login_required
def settings():
    return render_student_page("settings")


def build_analysis(career_name, text, user_id):
    career = CAREERS.get(career_name)
    if not career:
        career_name, career = next(iter(CAREERS.items()))
    found = extract_skills(text or "")
    required = career["skills"]
    progress_rows = db().execute(
        "SELECT skill, completed FROM roadmap_progress WHERE user_id=? AND career=?",
        (user_id, career_name),
    ).fetchall()
    completed = {row["skill"] for row in progress_rows if row["completed"]}
    skills = [
        {"name": skill, "have": skill in found, "level": details[0], "tip": details[1]}
        for skill, details in required.items()
    ]
    missing = [item for item in skills if not item["have"]]
    roadmap = []
    for level in (1, 2, 3):
        steps = []
        for item in missing:
            if item["level"] != level:
                continue
            skill = item["name"]
            links = RESOURCE_LINKS.get(skill, [("freeCodeCamp", "https://www.freecodecamp.org/learn/")])
            related_projects = [
                title for title, uses, _ in career["projects"] if skill in uses
            ]
            steps.append({
                **item,
                "completed": skill in completed,
                "hours": {1: "4–6 hours", 2: "6–10 hours", 3: "10–15 hours"}[level],
                "objective": f"Understand {skill} fundamentals and apply them to a practical outcome.",
                "practice": item["tip"],
                "resources": [{"label": label, "url": url} for label, url in links],
                "mini_project": related_projects[0] if related_projects else f"Create a small {skill} portfolio exercise",
            })
        if steps:
            roadmap.append({
                "phase": PHASES[level], "level_name": LEVEL_NAMES[level],
                "level": level, "steps": steps,
            })

    projects = []
    for title, uses, description in career["projects"]:
        gaps = [skill for skill in uses if any(item["name"] == skill for item in missing)]
        if gaps:
            projects.append({
                "title": title, "desc": description, "demonstrates": gaps,
                "saved": saved_exists(user_id, "project", title),
            })
    projects.sort(key=lambda item: -len(item["demonstrates"]))
    all_tasks = [step for phase in roadmap for step in phase["steps"]]
    done_count = sum(step["completed"] for step in all_tasks)
    return {
        "career": career_name, "score": round(100 * sum(item["have"] for item in skills) / len(skills)),
        "skills": skills, "next_up": [item["name"] for item in missing[:3]],
        "roadmap": roadmap, "projects": projects, "detected": sorted(found & ALL_SKILLS),
        "task_count": len(all_tasks), "completed_count": done_count,
        "pending_count": len(all_tasks) - done_count,
        "task_percent": round(100 * done_count / len(all_tasks)) if all_tasks else 100,
        "resources": [
            {"skill": item["name"], "links": RESOURCE_LINKS.get(
                item["name"], [("freeCodeCamp", "https://www.freecodecamp.org/learn/")]
            )}
            for item in skills
        ],
    }


def saved_exists(user_id, item_type, item_key):
    return db().execute(
        "SELECT 1 FROM saved_items WHERE user_id=? AND item_type=? AND item_key=?",
        (user_id, item_type, item_key),
    ).fetchone() is not None


@app.post("/api/analyze")
@login_required
def analyze():
    data = request.get_json(silent=True) or {}
    career_name = data.get("career")
    skills_text = data.get("skills")
    career = CAREERS.get(career_name) if isinstance(career_name, str) else None
    if not career:
        return jsonify(error="Choose a career from the list."), 400
    if not isinstance(skills_text, str):
        return jsonify(error="Enter your current skills as text."), 400
    text = skills_text.strip()
    if not text:
        return jsonify(error="List at least one skill you already have."), 400

    db().execute("UPDATE users SET skills=?, career_goal=? WHERE id=?",
                 (text[:2000], career_name, current_user()["id"]))
    db().commit()
    flash("Your skill analysis and personalized roadmap are saved.")
    result = build_analysis(career_name, text[:2000], current_user()["id"])
    return jsonify(result)


@app.post("/api/roadmap/progress")
@login_required
def update_roadmap_progress():
    data = request.get_json(silent=True) or {}
    career_name, skill = data.get("career"), data.get("skill")
    if (not isinstance(career_name, str) or career_name not in CAREERS
            or not isinstance(skill, str) or skill not in CAREERS[career_name]["skills"]):
        return jsonify(error="Choose a skill from your learning roadmap."), 400
    if not isinstance(data.get("completed"), bool):
        return jsonify(error="Choose whether this learning task is complete."), 400
    db().execute(
        """INSERT INTO roadmap_progress (user_id, career, skill, completed, updated_at)
           VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
           ON CONFLICT(user_id, career, skill) DO UPDATE SET
           completed=excluded.completed, updated_at=CURRENT_TIMESTAMP""",
        (current_user()["id"], career_name, skill, int(data["completed"])),
    )
    db().commit()
    updated = build_analysis(career_name, current_user()["skills"], current_user()["id"])
    return jsonify(
        completed_count=updated["completed_count"], pending_count=updated["pending_count"],
        task_percent=updated["task_percent"],
    )


@app.post("/api/saved")
@login_required
def toggle_saved_item():
    data = request.get_json(silent=True) or {}
    item_type, item_key, should_save = data.get("type"), data.get("key"), data.get("saved")
    title, details = "", ""
    if not isinstance(should_save, bool):
        return jsonify(error="Choose whether to save or remove this item."), 400
    current_career = CAREERS[current_user()["career_goal"]]
    if item_type == "skill" and isinstance(item_key, str):
        match = current_career["skills"].get(item_key)
        if not should_save and not match:
            match = next((info["skills"][item_key] for info in CAREERS.values()
                          if item_key in info["skills"]), None)
        if match:
            title, details = item_key, match[1]
    elif item_type == "project" and isinstance(item_key, str):
        match = next((project for project in current_career["projects"]
                      if project[0] == item_key), None)
        if not should_save and not match:
            match = next((project for info in CAREERS.values() for project in info["projects"]
                          if project[0] == item_key), None)
        if match:
            title, details = match[0], match[2]
    if not title:
        return jsonify(error="That skill or project cannot be saved."), 400
    if should_save:
        db().execute(
            "INSERT OR REPLACE INTO saved_items (user_id, item_type, item_key, title, details) VALUES (?, ?, ?, ?, ?)",
            (current_user()["id"], item_type, item_key, title, details),
        )
    else:
        db().execute(
            "DELETE FROM saved_items WHERE user_id=? AND item_type=? AND item_key=?",
            (current_user()["id"], item_type, item_key),
        )
    db().commit()
    return jsonify(saved=should_save)


@app.post("/api/settings")
@login_required
def update_settings():
    data = request.get_json(silent=True) or {}
    career = data.get("career")
    if not isinstance(career, str) or career not in CAREERS:
        return jsonify(error="Choose a career goal from the list."), 400
    db().execute("UPDATE users SET career_goal=? WHERE id=?", (career, current_user()["id"]))
    db().commit()
    flash("Learning preferences updated.")
    return jsonify(message="Career preference updated.")


if __name__ == "__main__":
    app.run(debug=True)
