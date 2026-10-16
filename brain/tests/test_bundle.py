"""Puki.exe carries the brain's files listed in tray.BRAIN_FILES: every local module they import
has to be on that list, or the brain fails to start on Windows (safety.py once wasn't)."""
import ast
import os

BRAIN = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def brain_files():
    tree = ast.parse(open(os.path.join(BRAIN, "tray.py"), encoding="utf-8").read())
    for node in ast.walk(tree):
        if isinstance(node, ast.Assign) and any(getattr(t, "id", "") == "BRAIN_FILES" for t in node.targets):
            return set(ast.literal_eval(node.value))
    raise AssertionError("no BRAIN_FILES in tray.py")


def local_imports(path):
    tree = ast.parse(open(path, encoding="utf-8").read())
    names = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            names |= {a.name.split(".")[0] for a in node.names}
        elif isinstance(node, ast.ImportFrom) and node.module and node.level == 0:
            names.add(node.module.split(".")[0])
    return {n for n in names if os.path.exists(os.path.join(BRAIN, n + ".py"))}


def test_every_local_import_is_bundled():
    bundled = brain_files()
    for f in sorted(bundled):
        if f.endswith(".py"):
            missing = {m + ".py" for m in local_imports(os.path.join(BRAIN, f))} - bundled
            assert not missing, f"{f} imports {missing}, which Puki.exe doesn't carry (tray.BRAIN_FILES)"
