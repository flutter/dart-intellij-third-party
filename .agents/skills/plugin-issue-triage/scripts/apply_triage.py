#!/usr/bin/env python3
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import subprocess
import json
import sys
import argparse
import os


def run_cmd(args):
    res = subprocess.run(args, capture_output=True, text=True, check=True)
    return res.stdout.strip()


def main():
    parser = argparse.ArgumentParser(
        description="Apply approved triage decisions to GitHub."
    )
    parser.add_argument(
        "--decisions-file", required=True, help="Path to triage_decisions.json."
    )
    args = parser.parse_args()

    decisions_path = os.path.abspath(os.path.expanduser(args.decisions_file))
    if not os.path.exists(decisions_path):
        print(
            f"Error: Decisions file '{decisions_path}' does not exist.", file=sys.stderr
        )
        sys.exit(1)

    with open(decisions_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    decisions = data["decisions"]
    approved_decisions = [d for d in decisions if d["approved"]]

    print(f"Applying {len(approved_decisions)} approved triage decisions to GitHub...")

    failed_issues = []

    for i, dec in enumerate(approved_decisions):
        issue_id = str(dec["id"])
        repo = dec.get("repo")
        priority = dec["priority"]
        assignee = dec["assignee"]
        labels = dec["labels"]
        reply = dec["reply"]

        print(f"\n[{i+1}/{len(approved_decisions)}] Processing Issue #{issue_id}...")

        try:
            # 1. Update Labels (Priority and component/type labels)
            current_labels = []
            issue_info_json = run_cmd(
                ["gh", "issue", "view", issue_id] + (["-R", repo] if repo else []) + [ "--json", "labels"]
            )
            if issue_info_json:
                try:
                    issue_info = json.loads(issue_info_json)
                    current_labels = [
                        l.get("name")
                        for l in issue_info.get("labels", [])
                        if l.get("name")
                    ]
                except Exception as e:
                    print(
                        f"  Warning: Failed to parse current labels: {e}",
                        file=sys.stderr,
                    )

            # Identify existing priority labels
            priority_labels = {"P0", "P1", "P2", "P3", "P4"}
            existing_p_labels = {l for l in current_labels if l in priority_labels}

            # Determine priority labels to add and remove
            remove_labels = []
            add_labels = []

            if priority in priority_labels:
                # Remove any other priority label
                remove_labels.extend(list(existing_p_labels - {priority}))
                # Add the new priority label if not already present
                if priority not in existing_p_labels:
                    add_labels.append(priority)
            else:
                # If new priority is "None", remove any existing priority label
                remove_labels.extend(list(existing_p_labels))

            # Ensure status: first-line-handled is added to all triaged issues
            if (
                "status: first-line-handled" not in current_labels
                and "status: first-line-handled" not in add_labels
            ):
                add_labels.append("status: first-line-handled")


            # Determine other non-priority component/type labels to add
            for l in labels:
                if l == "status: waiting-for-author-response":
                    continue
                if l and l not in current_labels and l not in add_labels:
                    add_labels.append(l)

            # Apply label changes via gh CLI
            if remove_labels:
                remove_str = ",".join(remove_labels)
                print(f"  Removing conflicting priority labels: {remove_str}")
                run_cmd(["gh", "issue", "edit", issue_id] + (["-R", repo] if repo else []) + [ "--remove-label", remove_str])

            if add_labels:
                add_str = ",".join(add_labels)
                print(f"  Adding labels: {add_str}")
                run_cmd(["gh", "issue", "edit", issue_id] + (["-R", repo] if repo else []) + [ "--add-label", add_str])

            # 2. Assignee
            if assignee:
                print(f"  Assigning to: @{assignee}")
                run_cmd(["gh", "issue", "edit", issue_id] + (["-R", repo] if repo else []) + [ "--add-assignee", assignee])

            # 3. Post Response Comment
            if reply:
                # Prepend the mandatory triage prefix if not already present
                prefix = "Plugin Triage: "
                comment_body = reply.strip()
                if not comment_body.startswith(prefix):
                    comment_body = prefix + comment_body

                already_posted = False
                comments_json = run_cmd(
                    ["gh", "issue", "view", issue_id] + (["-R", repo] if repo else []) + [ "--json", "comments"]
                )
                if comments_json:
                    try:
                        comments_data = json.loads(comments_json)
                        existing_comments = comments_data.get("comments", [])
                        for c in existing_comments:
                            if c["body"].strip() == comment_body:
                                already_posted = True
                                break
                    except Exception as e:
                        print(
                            f"  Warning: Failed to parse existing comments: {e}",
                            file=sys.stderr,
                        )

                if already_posted:
                    print(f"  Comment already posted. Skipping to prevent duplicate.")
                else:
                    print(f"  Posting comment...")
                    run_cmd(
                        ["gh", "issue", "comment", issue_id] + (["-R", repo] if repo else []) + [ "--body", comment_body]
                    )

            
            # 4. Handle GitHub Project 239
            add_to_project = dec.get("addToProject", False)
            current_iteration = dec.get("currentIteration", False)
            swarmable = dec.get("swarmable", False)

            if add_to_project:
                print(f"  Adding to DevExp GitHub Project (Flutter 239)...")
                issue_url = run_cmd(["gh", "issue", "view", issue_id] + (["-R", repo] if repo else []) + [ "--json", "url", "--jq", ".url"])
                if issue_url:
                    item_json = run_cmd(["gh", "project", "item-add", "239", "--owner", "flutter", "--url", issue_url, "--format", "json"])
                    if item_json:
                        try:
                            item_data = json.loads(item_json)
                            item_id = item_data.get("id")
                            if item_id and (current_iteration or swarmable):
                                proj_json = run_cmd(["gh", "project", "view", "239", "--owner", "flutter", "--format", "json"])
                                proj_id = json.loads(proj_json).get("id") if proj_json else None
                                
                                if proj_id:
                                    fields_json = run_cmd(["gh", "project", "field-list", "239", "--owner", "flutter", "--format", "json"])
                                    if fields_json:
                                        fields_data = json.loads(fields_json)
                                        fields = fields_data.get("fields", [])
                                        
                                        if current_iteration:
                                            iter_field = next((f for f in fields if f.get("name", "").lower() == "iteration"), None)
                                            if iter_field:
                                                iter_field_id = iter_field["id"]
                                                print(f"  Attempting to set Iteration to Current Iteration...")
                                                query = '''
                                                query {
                                                  node(id: "%s") {
                                                    ... on ProjectV2IterationField {
                                                      configuration {
                                                        iterations {
                                                          id
                                                          title
                                                          startDate
                                                          duration
                                                        }
                                                      }
                                                    }
                                                  }
                                                }
                                                ''' % iter_field_id
                                                iter_res = run_cmd(["gh", "api", "graphql", "-f", f"query={query}"])
                                                if iter_res:
                                                    iter_data = json.loads(iter_res)
                                                    iters = iter_data.get("data", {}).get("node", {}).get("configuration", {}).get("iterations", [])
                                                    
                                                    import datetime
                                                    today = datetime.date.today()
                                                    target_iter = None
                                                    for i in iters:
                                                        try:
                                                            s_date = datetime.datetime.strptime(i["startDate"], "%Y-%m-%d").date()
                                                            dur = int(i["duration"])
                                                            e_date = s_date + datetime.timedelta(days=dur)
                                                            if s_date <= today < e_date:
                                                                target_iter = i
                                                                break
                                                        except Exception:
                                                            pass
                                                    
                                                    if target_iter:
                                                        run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", iter_field_id, "--iteration-id", target_iter["id"]])
                                                        print(f"  Successfully set Iteration to '{target_iter['title']}'")
                                                    else:
                                                        print(f"  Warning: Could not determine current iteration.")
                                        
                                        if swarmable:
                                            swarm_field = next((f for f in fields if f.get("name", "").lower() == "swarmable"), None)
                                            if swarm_field:
                                                swarm_field_id = swarm_field["id"]
                                                options = swarm_field.get("options", [])
                                                if options:
                                                    target_opt = next((o for o in options if o["name"].lower() in ["yes", "true", "swarmable", "1"]), options[0])
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--single-select-option-id", target_opt["id"]])
                                                    print(f"  Successfully set Swarmable")
                                                else:
                                                    run_cmd(["gh", "project", "item-edit", "--id", item_id, "--project-id", proj_id, "--field-id", swarm_field_id, "--text", "Yes"])
                                                    print(f"  Successfully set Swarmable (Text)")
                        except Exception as ex:
                            print(f"  Warning: Failed to add/edit project item: {ex}", file=sys.stderr)



        except subprocess.CalledProcessError as e:
            print(
                f"  Error: Command '{' '.join(e.cmd)}' failed with exit code"
                f" {e.returncode}.",
                file=sys.stderr,
            )
            print(f"  Stderr: {e.stderr.strip()}", file=sys.stderr)
            print(
                f"  Skipping remaining updates for Issue #{issue_id}.", file=sys.stderr
            )
            failed_issues.append(issue_id)
            continue
        except Exception as e:
            print(
                f"  Error: Unexpected error updating Issue #{issue_id}: {e}",
                file=sys.stderr,
            )
            print(
                f"  Skipping remaining updates for Issue #{issue_id}.", file=sys.stderr
            )
            failed_issues.append(issue_id)
            continue

    if failed_issues:
        print(
            f"\nFinished with errors. Failed to apply updates for {len(failed_issues)}"
            f" issues: {', '.join(failed_issues)}"
        )
        sys.exit(1)
    else:
        print(
            "\nAll approved triage decisions have been successfully applied to GitHub!"
        )
        sys.exit(0)


if __name__ == "__main__":
    main()
