/*
 * SonarQube Flutter Plugin - Enables analysis of Dart and Flutter projects into SonarQube.
 * Copyright © 2020 inside|app (contact@insideapp.fr)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer;

import fr.insideapp.sonarqube.dart.lang.issues.dartanalyzer.executable.AnalyzerExecutable;

public class AnalyzerOutput {

    public enum Mode {
        DETECT,
        MACHINE,
        LEGACY;

        public static final Mode defaultMode = DETECT;
    }

    private final Mode mode;

    private final AnalyzerExecutable.Mode analyzerMode;

    private final String content;

    /**
     * Whether the analyzer ran against the plugin's bundled analysis_options.yaml
     * instead of the one SonarQube indexed. When it did, issues reported for that
     * file refer to different content and must not be recorded.
     */
    private final boolean analysisOptionsOverridden;

    public AnalyzerOutput(Mode mode, AnalyzerExecutable.Mode analyzerMode, String content) {
        this(mode, analyzerMode, content, false);
    }

    public AnalyzerOutput(Mode mode, AnalyzerExecutable.Mode analyzerMode, String content,
                          boolean analysisOptionsOverridden) {
        this.mode = mode;
        this.analyzerMode = analyzerMode;
        this.content = content;
        this.analysisOptionsOverridden = analysisOptionsOverridden;
    }

    public boolean isAnalysisOptionsOverridden() {
        return analysisOptionsOverridden;
    }

    public Mode getMode() {
        return mode;
    }

    public AnalyzerExecutable.Mode getAnalyzerMode() {
        return this.analyzerMode;
    }

    public String getContent() {
        return content;
    }
}
