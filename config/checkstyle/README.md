# Checkstyle Configuration

Wildbook uses [Checkstyle](https://checkstyle.org/) 12.x (via `maven-checkstyle-plugin` 3.6.x) to enforce a curated set of code quality rules. The check runs automatically during `mvn verify`.

## Ruleset philosophy

The rules target real problems — banned APIs, correctness bugs, import hygiene — not cosmetic formatting. The codebase predates this tooling and uses 4-space indentation throughout; adopting a style guide that mandates 2-space indentation (e.g., Google Java Style) would require reformatting every existing file without improving correctness. Rules are added here when they catch real defects or replace a banned pattern with a better one.

## What is enforced

| Rule | Why |
|---|---|
| No `System.out` / `System.err` | Use a Log4j2 logger instead |
| No bare `e.printStackTrace()` | Use `logger.error(message, exception)` |
| No wildcard imports (`import java.util.*`) | Makes dependencies explicit |
| No unused imports | Reduces noise |
| No `sun.*` imports | Not part of the public API |
| No tab characters | Consistent whitespace across editors |
| Lines ≤ 120 characters | Readable without horizontal scrolling |
| No trailing whitespace | Clean diffs |
| Files must end with a newline | POSIX standard; avoids diff noise |
| `equals()` and `hashCode()` overridden together | Correctness — violating this breaks `HashMap`, `HashSet` |

**Logger naming convention (code review, not automated):** Static Log4j2 logger fields must be named `logger` (lowercase). This is enforced during code review rather than by Checkstyle, which cannot scope a naming rule to a specific field type without a custom module.

## New code

New files and new classes must pass all rules with **no suppression entry**. Write clean code from the start:

- Use `import org.apache.logging.log4j.LogManager;` and `import org.apache.logging.log4j.Logger;` — not `java.util.logging` or SLF4J directly
- Declare the logger as the first field: `private static final Logger logger = LogManager.getLogger(ClassName.class);`
- Never use `System.out`, `System.err`, or `e.printStackTrace()` — use the logger
- Use explicit imports, not wildcards
- Keep lines under 120 characters

To check your changes locally before pushing:

```bash
mvn checkstyle:check
```

Or as part of the full build:

```bash
mvn verify -DskipTests
```

A violation in a new file will fail the build. Fix the violation — do not add a suppression entry for new code.

## Existing code and suppressions

`config/checkstyle/suppressions.xml` is a baseline suppression list generated when Checkstyle was first introduced. Every file that had violations at that point received a blanket `checks=".*"` suppression, making the build green immediately without requiring a big-bang cleanup.

**The suppression list is meant to shrink over time.** When a task modifies an existing file:

1. Remove that file's `<suppress>` entry from `suppressions.xml`
2. Run `mvn checkstyle:check` to see all violations in that file
3. Fix them as part of the task
4. Commit the updated file and the updated `suppressions.xml` together

Do not add new `<suppress>` entries for files you are actively working on. If a pre-existing file has too many violations to fix in one task, fix what you can and leave its entry in place — but note the remaining violations in the commit message so they can be tracked.

### Regenerating the baseline (when needed)

If a rule is added or widened and new violations appear in files that are not yet being cleaned up, regenerate the suppressions:

```bash
mvn checkstyle:checkstyle -q 2>/dev/null || true
python3 - <<'EOF' > config/checkstyle/suppressions.xml
import xml.etree.ElementTree as ET, os

tree = ET.parse("target/checkstyle-result.xml")
root = tree.getroot()
files_with_violations = set()
for file_elem in root.findall("file"):
    if file_elem.findall("error"):
        name = file_elem.get("name")
        files_with_violations.add(os.path.basename(name))

lines = [
    '<?xml version="1.0"?>',
    '<!DOCTYPE suppressions PUBLIC',
    '    "-//Checkstyle//DTD SuppressionFilter Configuration 1.2//EN"',
    '    "https://checkstyle.org/dtds/suppressions_1_2.dtd">',
    '<!-- Auto-generated baseline suppressions. Remove an entry when its file is cleaned up. -->',
    '<suppressions>',
]
for f in sorted(files_with_violations):
    lines.append(f'    <suppress files="{f}" checks=".*"/>')
lines.append('</suppressions>')
print('\n'.join(lines))
EOF
```

After regenerating, verify that recently-cleaned files have not re-acquired suppression entries.

## Adding new rules

Edit `config/checkstyle/wildbook_checks.xml`. Run `mvn checkstyle:checkstyle` to generate the violations report, then regenerate `suppressions.xml` to baseline any new violations in existing files. New files must still pass without suppression.

Prefer rules that catch correctness problems (`EqualsHashCode`, `StringLiteralEquality`, `FallThrough`) over purely cosmetic ones. Each rule added here is a permanent enforcement gate — consider whether the signal-to-noise ratio justifies it before adding.

## Files

| File | Purpose |
|---|---|
| `wildbook_checks.xml` | Checkstyle ruleset |
| `suppressions.xml` | Baseline suppressions for pre-existing violations |
| `README.md` | This document |
