import json

file_path = "/Users/pquitslund/.gemini/jetski/brain/5960521d-de87-42a5-a239-44099506d0d4/scratch/issues_to_triage_dart.json"
with open(file_path, "r") as f:
    data = json.load(f)

# Put CODEOWNERS first, then some other assignees
data["assignees"] = [
    {"handle": "helin24", "name": "helin24"},
    {"handle": "pq", "name": "pq"},
    {"handle": "bwilkerson", "name": "bwilkerson"},
    {"handle": "jwren", "name": "jwren"},
    {"handle": "devoncarew", "name": "devoncarew"}
]

# Set assignee to empty string for all issues so it shows "Select an assignee..."
for issue_id, issue_data in data.get("decisions", {}).items():
    issue_data["assignee"] = ""
    issue_data["assignee_reason"] = "Assignee left blank by default."

with open(file_path, "w") as f:
    json.dump(data, f, indent=2)

print("Updated test JSON.")
