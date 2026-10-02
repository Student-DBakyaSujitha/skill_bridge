const $ = id => document.getElementById(id);
const csrf = document.querySelector('meta[name="csrf"]')?.content || "";
const message = $("action-message");

function showMessage(text, isError = false) {
  if (!message) return;
  message.textContent = text;
  message.className = isError ? "notice error-notice" : "notice";
  message.hidden = false;
}

async function postJSON(url, body) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf },
    body: JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || "The request could not be completed.");
  return data;
}

const analysisForm = $("form");
if (analysisForm) {
  analysisForm.addEventListener("submit", async event => {
    event.preventDefault();
    const button = analysisForm.querySelector("button");
    const error = $("error");
    error.hidden = true;
    button.disabled = true;
    button.textContent = "Analyzing…";
    $("prog").parentElement.classList.add("busy");
    try {
      await postJSON("/api/analyze", {
        career: $("career").value,
        skills: $("skills").value
      });
      showMessage("Your skills and personalized roadmap have been saved. Updating your dashboard…");
      setTimeout(() => window.location.reload(), 700);
    } catch (problem) {
      error.textContent = problem.message;
      error.hidden = false;
    } finally {
      $("prog").parentElement.classList.remove("busy");
      button.disabled = false;
      button.textContent = "Analyze my skills";
    }
  });
}

document.querySelectorAll("[data-roadmap-task]").forEach(checkbox => {
  checkbox.addEventListener("change", async () => {
    const previous = !checkbox.checked;
    checkbox.disabled = true;
    showMessage("Saving your roadmap progress…");
    try {
      const result = await postJSON("/api/roadmap/progress", {
        career: checkbox.dataset.career,
        skill: checkbox.dataset.skill,
        completed: checkbox.checked
      });
      document.querySelectorAll("[data-completed-count]").forEach(node => {
        node.textContent = result.completed_count;
      });
      document.querySelectorAll("[data-pending-count]").forEach(node => {
        node.textContent = result.pending_count;
      });
      document.querySelectorAll("[data-task-percent]").forEach(node => {
        node.textContent = `${result.task_percent}%`;
      });
      document.querySelectorAll("[data-task-progress]").forEach(node => {
        node.style.width = `${result.task_percent}%`;
      });
      showMessage(checkbox.checked ? "Learning task marked complete." : "Learning task moved back to pending.");
    } catch (problem) {
      checkbox.checked = previous;
      showMessage(problem.message, true);
    } finally {
      checkbox.disabled = false;
    }
  });
});

document.querySelectorAll("[data-save-toggle]").forEach(button => {
  button.addEventListener("click", async () => {
    const previous = button.dataset.saved === "true";
    const next = !previous;
    button.disabled = true;
    button.textContent = "Saving…";
    try {
      await postJSON("/api/saved", {
        type: button.dataset.type,
        key: button.dataset.key,
        saved: next
      });
      button.dataset.saved = String(next);
      button.textContent = next ? "Remove saved" :
        (button.dataset.type === "skill" ? "Save skill" : "Save project");
      showMessage(next ? "Saved to your account." : "Removed from saved items.");
      if (!next && button.closest("[data-saved-card]")) {
        button.closest("[data-saved-card]").remove();
        const listElement = $("saved-list");
        if (listElement && !listElement.querySelector("[data-saved-card]")) {
          const empty = document.createElement("p");
          empty.className = "muted";
          empty.textContent = "No saved items yet. Save skills or projects to find them here.";
          listElement.append(empty);
        }
      }
    } catch (problem) {
      showMessage(problem.message, true);
    } finally {
      button.textContent = button.dataset.saved === "true" ? "Remove saved" :
        (button.dataset.type === "skill" ? "Save skill" : "Save project");
      button.disabled = false;
    }
  });
});

const settingsForm = $("settings-form");
if (settingsForm) {
  settingsForm.addEventListener("submit", async event => {
    event.preventDefault();
    const button = settingsForm.querySelector("button");
    button.disabled = true;
    button.textContent = "Saving…";
    try {
      await postJSON("/api/settings", { career: $("settings-career").value });
      showMessage("Preferences saved. Refreshing your personalized recommendations…");
      setTimeout(() => window.location.reload(), 700);
    } catch (problem) {
      showMessage(problem.message, true);
      button.textContent = "Save preferences";
      button.disabled = false;
    }
  });
}
