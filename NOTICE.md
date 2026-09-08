# NOTICE

This project is an independent copy (fork) of
[insideapp-fr/sonar-flutter](https://github.com/insideapp-fr/sonar-flutter),
maintained at [lenerson/sonar-flutter](https://github.com/lenerson/sonar-flutter).

It has been **modified** since the copy was taken on 2026-09-08. The first
modification modernizes the plugin for SonarQube Community Build 26.x by
migrating from `sonar-plugin-api` 7.9 to 13.8.0.4399.

The complete commit history of the original project is preserved in this
repository.

---

## Original work

    SonarQube Flutter Plugin - Enables analysis of Dart and Flutter projects
    into SonarQube.
    Copyright © 2020 inside|app (contact@insideapp.fr)

Licensed under the **GNU Lesser General Public License v3.0 or later**
(LGPL-3.0-or-later). See [LICENSE.md](LICENSE.md) for the full text.

This program is distributed in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
PARTICULAR PURPOSE.

Because this project is a derivative work of the above, it remains licensed
under LGPL-3.0-or-later. The original copyright notices in every source file
are retained unchanged.

---

## Third-party components

### Dart2 ANTLR grammar — BSD 3-Clause

`dart-lang/src/main/antlr/Dart2.g4` and the parser sources generated from it
under `dart-lang/src/main/java/fr/insideapp/sonarqube/dart/lang/antlr/generated/`
derive from a grammar published under the BSD 3-Clause License:

    [The "BSD licence"]
    Copyright (c) 2019 Wener
    All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:
    1. Redistributions of source code must retain the above copyright notice,
       this list of conditions and the following disclaimer.
    2. Redistributions in binary form must reproduce the above copyright
       notice, this list of conditions and the following disclaimer in the
       documentation and/or other materials provided with the distribution.
    3. The name of the author may not be used to endorse or promote products
       derived from this software without specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
    IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
    OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
    IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
    INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

The full notice is preserved at the top of `Dart2.g4`.

### Dart linter rule descriptions — CC BY 4.0

`dart-lang/src/main/resources/dartanalyzer/rules.json` carries rule names and
HTML descriptions collected from the Dart documentation site
(<https://dart.dev/tools/linter-rules>) by
`scripts/updateDartAnalyzerRules.groovy`.

Content on dart.dev is published by Google under the
[Creative Commons Attribution 4.0 International License](https://creativecommons.org/licenses/by/4.0/),
and its code samples under the 3-Clause BSD License. See
<https://dart.dev/terms>.

Dart and the Dart logo are trademarks of Google LLC.

### Trademarks

SonarQube, SonarCloud and Sonar are trademarks of SonarSource SA. This project
is an independent, community-maintained plugin and is **not** affiliated with,
endorsed by, or sponsored by SonarSource SA.

`inside|app` is a trademark of its respective owner. Its appearance in
copyright notices records authorship of the original work and does not imply
endorsement of this modified version.
