"""Print public Kotlin/Java declaration headers from sources jars (no bodies, no KDoc).

Usage: python3 api_signatures.py <jar> <file-name regex>
Lets code be written against the exact published API when Google Maven is not reachable.
"""
import re
import sys
import zipfile

jar, pattern = sys.argv[1], re.compile(sys.argv[2])
decl = re.compile(r'^\s*(public |internal |protected |override |open |abstract |actual |inline |suspend |data |sealed |enum |value |@\w+(\([^)]*\))? )*(fun|class|object|interface|val|var|const val|typealias)\b')
skip = re.compile(r'^\s*(private|internal)\b')

with zipfile.ZipFile(jar) as z:
    for name in sorted(z.namelist()):
        base = name.rsplit('/', 1)[-1]
        if not (name.endswith('.kt') or name.endswith('.java')) or not pattern.search(base):
            continue
        lines = z.read(name).decode('utf-8', 'replace').splitlines()
        out = []
        i = 0
        while i < len(lines):
            line = lines[i]
            if decl.match(line) and not skip.match(line) and 'private ' not in line.split('fun')[0]:
                # Collect until parentheses balance and the header ends
                header = [line.rstrip()]
                depth = line.count('(') - line.count(')')
                j = i
                while depth > 0 and j + 1 < len(lines) and len(header) < 60:
                    j += 1
                    header.append(lines[j].rstrip())
                    depth += lines[j].count('(') - lines[j].count(')')
                text = '\n'.join(header)
                text = re.sub(r'\{[^{}]*$', '', text).rstrip(' {=')
                out.append(text)
                i = j + 1
                continue
            i += 1
        if out:
            print(f'===== {name}')
            print('\n'.join(out))
