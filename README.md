SkillBridge
Compare your skills with a chosen career, get a learning roadmap and portfolio project ideas.
    pip install -r requirements.txt
    python app.py          # open http://127.0.0.1:5000

Pages: `/` home, `/register`, `/login`, `/dashboard`, `/profile`. Accounts live in `instance/skillbridge.db`.
Before deploying: serve over HTTPS and set `PRODUCTION=1` and a persistent `SECRET_KEY`.
Careers and skills are plain dictionaries at the top of `app.py`.