import sys
import re

file_path = "launch_dashboard.py"
with open(file_path, "r") as f:
    content = f.read()

import_pattern = re.compile(r"import time\n")
import_replacement = "import time\nimport subprocess\nimport datetime\n"

content = import_pattern.sub(import_replacement, content)

config_endpoint = """        elif parsed_url.path == "/api/config":
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(json.dumps({"currentIteration": self.server.current_iteration}).encode("utf-8"))
        else:
            self.send_response(404)"""

content = content.replace("        else:\n            self.send_response(404)", config_endpoint)

fetch_logic = """    # Pass paths to handler class
    TriageDashboardHandler.data_file = resolved_data_file
    TriageDashboardHandler.output_file = resolved_output_file
    
    current_iteration = None
    try:
        proj_json = subprocess.run(["gh", "project", "view", "239", "--owner", "flutter", "--format", "json"], capture_output=True, text=True, timeout=5)
        if proj_json.returncode == 0:
            proj_data = json.loads(proj_json.stdout)
            fields_json = subprocess.run(["gh", "project", "field-list", "239", "--owner", "flutter", "--format", "json"], capture_output=True, text=True, timeout=5)
            if fields_json.returncode == 0:
                fields_data = json.loads(fields_json.stdout)
                fields = fields_data.get("fields", [])
                iter_field = next((f for f in fields if f.get("name", "").lower() == "iteration"), None)
                if iter_field:
                    iter_field_id = iter_field["id"]
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
                    iter_res = subprocess.run(["gh", "api", "graphql", "-f", f"query={query}"], capture_output=True, text=True, timeout=5)
                    if iter_res.returncode == 0:
                        iter_data = json.loads(iter_res.stdout)
                        iters = iter_data.get("data", {}).get("node", {}).get("configuration", {}).get("iterations", [])
                        today = datetime.date.today()
                        for i in iters:
                            try:
                                s_date = datetime.datetime.strptime(i["startDate"], "%Y-%m-%d").date()
                                dur = int(i["duration"])
                                e_date = s_date + datetime.timedelta(days=dur)
                                if s_date <= today < e_date:
                                    current_iteration = i["title"]
                                    break
                            except Exception:
                                pass
    except Exception:
        pass
    
    # Bind to random port"""

content = content.replace("    # Pass paths to handler class\n    TriageDashboardHandler.data_file = resolved_data_file\n    TriageDashboardHandler.output_file = resolved_output_file\n\n    # Bind to random port", fetch_logic)

bind_logic = """    httpd = http.server.ThreadingHTTPServer(server_address, TriageDashboardHandler)
    httpd.current_iteration = current_iteration
    port = httpd.server_port"""

content = content.replace("    httpd = http.server.ThreadingHTTPServer(server_address, TriageDashboardHandler)\n    port = httpd.server_port", bind_logic)


with open(file_path, "w") as f:
    f.write(content)
print("Patched launch_dashboard.py successfully.")
