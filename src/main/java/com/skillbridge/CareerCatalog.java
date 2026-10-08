package com.skillbridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CareerCatalog {
    public record Skill(int level, String tip) {}
    public record Project(String title, List<String> skills, String description) {}
    public record Link(String label, String url) {}
    public record Career(LinkedHashMap<String, Skill> skills, List<Project> projects) {}

    public static final Map<String, Career> CAREERS;
    public static final Map<String, String> ALIASES;
    public static final Set<String> ALL_SKILLS;
    public static final Map<String, List<Link>> RESOURCE_LINKS;
    public static final List<String> DEPARTMENTS = List.of(
            "Computer Science", "Information Technology", "Electronics and Communication",
            "Electrical Engineering", "Mechanical Engineering", "Civil Engineering",
            "Business Administration", "Data Science", "Other");
    public static final List<Integer> YEARS = List.of(1, 2, 3, 4, 5, 6);
    public static final Map<Integer, String> PHASES = Map.of(
            1, "Foundations", 2, "Core skills", 3, "Advanced and job-ready");
    public static final Map<Integer, String> LEVEL_NAMES = Map.of(
            1, "Beginner", 2, "Intermediate", 3, "Advanced");

    static {
        LinkedHashMap<String, Career> careers = new LinkedHashMap<>();

        LinkedHashMap<String, Skill> webSkills = new LinkedHashMap<>();
        add(webSkills, "html", 1, "Build 3 static pages using semantic tags.");
        add(webSkills, "css", 1, "Recreate a site layout with flexbox and grid.");
        add(webSkills, "javascript", 1, "Add DOM interactivity: forms, fetch, events.");
        add(webSkills, "git", 1, "Commit, branch and open a pull request on GitHub.");
        add(webSkills, "responsive design", 2, "Make one page work from 360px to 1440px.");
        add(webSkills, "rest apis", 2, "Build and call a small JSON API.");
        add(webSkills, "sql", 2, "Model two related tables and write joins.");
        add(webSkills, "react", 2, "Rebuild a small page as components with state.");
        add(webSkills, "testing", 3, "Write unit tests for one API route.");
        add(webSkills, "deployment", 3, "Deploy an app and set environment variables.");
        careers.put("Web Developer", new Career(webSkills, List.of(
                project("Personal portfolio site", List.of("html", "css", "responsive design", "git"),
                        "Show 3 projects with a contact form."),
                project("Expense tracker with an API", List.of("javascript", "rest apis", "sql"),
                        "Add, edit and chart expenses stored in a database."),
                project("Deployed team task board", List.of("react", "testing", "deployment"),
                        "Live URL with tests and a README."))));

        LinkedHashMap<String, Skill> dataSkills = new LinkedHashMap<>();
        add(dataSkills, "excel", 1, "Practice pivot tables and lookups on a public dataset.");
        add(dataSkills, "sql", 1, "Answer 20 business questions with SELECT, JOIN, GROUP BY.");
        add(dataSkills, "statistics", 1, "Cover mean, variance, distributions and hypothesis tests.");
        add(dataSkills, "python", 2, "Write scripts that read, filter and summarize CSVs.");
        add(dataSkills, "pandas", 2, "Clean a messy dataset: nulls, types, duplicates.");
        add(dataSkills, "data visualization", 2, "Choose the right chart and label it clearly.");
        add(dataSkills, "tableau", 2, "Build one interactive dashboard with filters.");
        add(dataSkills, "a/b testing", 3, "Analyze a sample experiment and report significance.");
        add(dataSkills, "storytelling", 3, "Present findings in a 5-slide summary for non-experts.");
        careers.put("Data Analyst", new Career(dataSkills, List.of(
                project("Sales dashboard", List.of("sql", "excel", "tableau", "data visualization"),
                        "Track revenue by region and month."),
                project("Public dataset cleanup and analysis", List.of("python", "pandas", "statistics"),
                        "Publish a notebook with cleaning steps and insights."),
                project("Experiment readout", List.of("a/b testing", "storytelling"),
                        "Report whether a change improved conversion."))));

        LinkedHashMap<String, Skill> mlSkills = new LinkedHashMap<>();
        add(mlSkills, "python", 1, "Get fluent with functions, classes and virtual environments.");
        add(mlSkills, "statistics", 1, "Study probability, bias/variance and evaluation metrics.");
        add(mlSkills, "linear algebra", 1, "Review vectors, matrices and gradients.");
        add(mlSkills, "pandas", 2, "Prepare features from a raw dataset.");
        add(mlSkills, "sql", 2, "Pull training data with joins and aggregations.");
        add(mlSkills, "machine learning", 2, "Train and compare 3 models on one problem.");
        add(mlSkills, "scikit-learn", 2, "Use pipelines and cross-validation.");
        add(mlSkills, "deep learning", 3, "Train a small neural net and plot its loss.");
        add(mlSkills, "pytorch", 3, "Implement a training loop yourself.");
        add(mlSkills, "mlops", 3, "Track experiments and serve a model behind an API.");
        careers.put("Machine Learning Engineer", new Career(mlSkills, List.of(
                project("House price predictor", List.of("python", "pandas", "scikit-learn", "machine learning"),
                        "Compare models and explain errors."),
                project("Image classifier", List.of("deep learning", "pytorch"),
                        "Train on a small dataset and report accuracy."),
                project("Model served as an API", List.of("mlops", "python", "sql"),
                        "Containerize a model with a prediction endpoint."))));
        CAREERS = Collections.unmodifiableMap(careers);

        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        alias(aliases, "js", "javascript");
        alias(aliases, "ecmascript", "javascript");
        alias(aliases, "node", "javascript");
        alias(aliases, "html5", "html");
        alias(aliases, "css3", "css");
        alias(aliases, "flexbox", "css");
        alias(aliases, "tailwind", "css");
        alias(aliases, "bootstrap", "css");
        alias(aliases, "github", "git");
        alias(aliases, "version control", "git");
        alias(aliases, "api", "rest apis");
        alias(aliases, "apis", "rest apis");
        alias(aliases, "flask", "rest apis");
        alias(aliases, "fastapi", "rest apis");
        alias(aliases, "express", "rest apis");
        alias(aliases, "mysql", "sql");
        alias(aliases, "postgres", "sql");
        alias(aliases, "postgresql", "sql");
        alias(aliases, "sqlite", "sql");
        alias(aliases, "databases", "sql");
        alias(aliases, "reactjs", "react");
        alias(aliases, "react.js", "react");
        alias(aliases, "pytest", "testing");
        alias(aliases, "unit tests", "testing");
        alias(aliases, "jest", "testing");
        alias(aliases, "docker", "deployment");
        alias(aliases, "heroku", "deployment");
        alias(aliases, "aws", "deployment");
        alias(aliases, "py", "python");
        alias(aliases, "numpy", "python");
        alias(aliases, "stats", "statistics");
        alias(aliases, "probability", "statistics");
        alias(aliases, "matplotlib", "data visualization");
        alias(aliases, "seaborn", "data visualization");
        alias(aliases, "power bi", "tableau");
        alias(aliases, "charts", "data visualization");
        alias(aliases, "ml", "machine learning");
        alias(aliases, "sklearn", "scikit-learn");
        alias(aliases, "tensorflow", "deep learning");
        alias(aliases, "keras", "deep learning");
        alias(aliases, "neural networks", "deep learning");
        alias(aliases, "matrices", "linear algebra");
        alias(aliases, "ab testing", "a/b testing");
        alias(aliases, "presenting", "storytelling");
        alias(aliases, "mobile first", "responsive design");
        alias(aliases, "responsive", "responsive design");
        ALIASES = Collections.unmodifiableMap(aliases);

        LinkedHashSet<String> allSkills = new LinkedHashSet<>();
        careers.values().forEach(career -> allSkills.addAll(career.skills().keySet()));
        ALL_SKILLS = Collections.unmodifiableSet(allSkills);

        LinkedHashMap<String, List<Link>> resources = new LinkedHashMap<>();
        link(resources, "html", "MDN HTML", "https://developer.mozilla.org/en-US/docs/Learn/HTML");
        link(resources, "css", "MDN CSS", "https://developer.mozilla.org/en-US/docs/Learn/CSS");
        link(resources, "javascript", "MDN JavaScript", "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide");
        link(resources, "git", "GitHub Skills", "https://skills.github.com/");
        link(resources, "responsive design", "web.dev responsive design", "https://web.dev/learn/design/");
        link(resources, "rest apis", "MDN HTTP overview", "https://developer.mozilla.org/en-US/docs/Web/HTTP/Overview");
        link(resources, "sql", "SQLBolt", "https://sqlbolt.com/");
        link(resources, "react", "React Learn", "https://react.dev/learn");
        link(resources, "testing", "Testing Library", "https://testing-library.com/docs/");
        link(resources, "deployment", "Render deployment guides", "https://render.com/docs/deploys");
        link(resources, "excel", "Microsoft Excel help", "https://support.microsoft.com/excel");
        link(resources, "statistics", "Khan Academy statistics", "https://www.khanacademy.org/math/statistics-probability");
        link(resources, "python", "Python tutorial", "https://docs.python.org/3/tutorial/");
        link(resources, "pandas", "Pandas getting started", "https://pandas.pydata.org/docs/getting_started/index.html");
        link(resources, "data visualization", "Datawrapper Academy", "https://academy.datawrapper.de/");
        link(resources, "tableau", "Tableau free training", "https://www.tableau.com/learn/training");
        link(resources, "a/b testing", "Optimizely A/B testing guide", "https://www.optimizely.com/optimization-glossary/ab-testing/");
        link(resources, "storytelling", "Storytelling with data", "https://www.storytellingwithdata.com/");
        link(resources, "linear algebra", "Khan Academy linear algebra", "https://www.khanacademy.org/math/linear-algebra");
        link(resources, "machine learning", "Google Machine Learning Crash Course", "https://developers.google.com/machine-learning/crash-course");
        link(resources, "scikit-learn", "scikit-learn tutorials", "https://scikit-learn.org/stable/tutorial/index.html");
        link(resources, "deep learning", "Dive into Deep Learning", "https://d2l.ai/");
        link(resources, "pytorch", "PyTorch tutorials", "https://pytorch.org/tutorials/");
        link(resources, "mlops", "Made With ML", "https://madewithml.com/");
        RESOURCE_LINKS = Collections.unmodifiableMap(resources);
    }

    private CareerCatalog() {}

    private static void add(Map<String, Skill> skills, String name, int level, String tip) {
        skills.put(name, new Skill(level, tip));
    }

    private static Project project(String title, List<String> skills, String description) {
        return new Project(title, List.copyOf(skills), description);
    }

    private static void alias(Map<String, String> aliases, String spelling, String canonical) {
        aliases.put(spelling, canonical);
    }

    private static void link(Map<String, List<Link>> resources, String skill, String label, String url) {
        List<Link> links = new ArrayList<>(resources.getOrDefault(skill, List.of()));
        links.add(new Link(label, url));
        resources.put(skill, List.copyOf(links));
    }
}
