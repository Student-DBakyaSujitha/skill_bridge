# SkillBridge

Compare your skills with a chosen career, get a learning roadmap and portfolio project ideas.
The signed-in workspace includes skill-gap analysis, curated learning resources, saved items,
and a progress tracker. Roadmap task completion and saved skills/projects are stored per student
in SQLite and remain available after refreshing or signing back in.

    pip install -r requirements.txt
    python app.py          # open http://127.0.0.1:5000

Pages: `/` home, `/register`, `/login`, `/dashboard`, `/profile`, `/skills`, `/analysis`,
`/roadmap`, `/projects`, `/resources`, `/progress`, `/saved`, and `/settings`. Accounts and
student learning progress live in `instance/skillbridge.db`.
Before deploying: serve over HTTPS and set `PRODUCTION=1` and a persistent `SECRET_KEY`.
Careers and skills are plain dictionaries at the top of `app.py`.
