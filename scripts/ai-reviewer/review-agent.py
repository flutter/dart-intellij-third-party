import os
import json
import sys
from github import Github
import google.generativeai as genai

# Setup
GITHUB_TOKEN = os.getenv('GITHUB_TOKEN')
GEMINI_API_KEY = os.getenv('GEMINI_API_KEY')
HUMAN_REVIEWER = os.getenv('HUMAN_REVIEWER')  # GitHub username of the human reviewer
PR_NUMBER = int(sys.argv[1])

genai.configure(api_key=GEMINI_API_KEY)
gh = Github(GITHUB_TOKEN)
repo = gh.get_repo("flutter/flutter-intellij")
pr = repo.get_pull(PR_NUMBER)

def get_line_specific_review():
    with open("docs/ai-standards.md", "r") as f:
        standards = f.read()

    # We need the commit SHA for the "position" of the comment
    latest_commit = pr.get_commits().reversed[0]

    # Get the diff
    comparison = repo.compare(pr.base.sha, pr.head.sha)
    diff_content = ""
    for file in comparison.files:
        diff_content += f"File: {file.filename}\n{file.patch}\n\n"

    prompt = f"""
    Review this PR for the Flutter IntelliJ plugin based on these standards:
    {standards}

    PR DIFF:
    {diff_content}

    INSTRUCTIONS:
    Return your review as a JSON list of objects. Each object MUST have:
    "path": (the file path),
    "line": (the line number in the new code),
    "comment": (your feedback).

    If no issues are found, return an empty list [].
    Finally, append the string "ACTION: READY_FOR_HUMAN_REVIEW" at the very end outside the JSON.
    """

    model = genai.GenerativeModel('gemini-1.5-pro')
    response = model.generate_content(prompt)
    return response.text, latest_commit

def apply_line_comments(raw_response, commit):
    # Split the JSON part from the Action string
    try:
        json_str = raw_response.split("ACTION:")[0].strip()
        comments = json.loads(json_str)

        for c in comments:
            # This creates the actual line-level comment on the PR
            pr.create_review_comment(
                body=c['comment'],
                commit=commit,
                path=c['path'],
                line=int(c['line'])
            )
        
        if "READY_FOR_HUMAN_REVIEW" in raw_response:
            pr.add_to_assignees(HUMAN_REVIEWER)
            pr.create_issue_comment(f"AI Review complete. Reassigning to @{HUMAN_REVIEWER} for final sign-off.")

    except Exception as e:
        print(f"Error parsing AI response: {e}")
        # Fallback: post the raw response as a single comment if JSON fails
        pr.create_issue_comment(f"AI Review Error: {raw_response}")

if __name__ == "__main__":
    raw_text, commit = get_line_specific_review()
    apply_line_comments(raw_text, commit)