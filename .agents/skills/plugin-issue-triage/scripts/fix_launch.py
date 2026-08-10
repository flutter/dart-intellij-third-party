import sys

file_path = "launch_dashboard.py"
with open(file_path, "r") as f:
    content = f.read()

import re

# We will regex replace the whole fetch logic block with just datetime month.
pattern = re.compile(r"    current_iteration = None\n    try:.*?    except Exception:\n        pass", re.DOTALL)

new_block = """    import datetime
    current_iteration = datetime.date.today().strftime("%B")"""

if pattern.search(content):
    content = pattern.sub(new_block, content)
    with open(file_path, "w") as f:
        f.write(content)
    print("Patched launch_dashboard.py successfully.")
else:
    print("Could not find insertion point in launch_dashboard.py")
