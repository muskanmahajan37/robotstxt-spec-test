// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.search.robotstxt.spec;

import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.search.robotstxt.spec.specification.SpecificationProtos;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Handles a parser that outputs its outcome by printing at stdout */
public class PrintingParserMatcher implements ParserMatcher {
  @Override
  public TestOutcome getOutcome(
      byte[] robotsTxtContent, String url, String userAgent, CMDArgs cmdArgs) throws Exception {
    // Create temporary file for the robots.txt content and pass the path as argument
    File robotsTxtPath = File.createTempFile("robots_", ".tmp.txt");
    Files.asByteSink(robotsTxtPath).write(robotsTxtContent);

    // Run the parser
    Process process = cmdArgs.runParser(robotsTxtPath, url, userAgent);
    process.waitFor();

    String stdOut = CMDArgs.outputToString(process.getInputStream());
    TestOutcome.Builder testOutcome =
        TestOutcome.builder()
            .setStdOut(stdOut)
            .setStdErr(CMDArgs.outputToString(process.getErrorStream()))
            .setExitCode(process.exitValue())
            .setOutcome(SpecificationProtos.Outcome.UNSPECIFIED);

    Pattern allowedPattern = Pattern.compile(cmdArgs.getAllowedPattern());
    Pattern disallowedPattern = Pattern.compile(cmdArgs.getDisallowedPattern());

    // Pattern-matching for the regular expression
    for (String line : Splitter.on('\n').split(stdOut)) {
      Matcher allowedMatcher = allowedPattern.matcher(line);
      Matcher disallowedMatcher = disallowedPattern.matcher(line);

      // Test the outcome
      if (disallowedMatcher.find()) {
        testOutcome.setOutcome(SpecificationProtos.Outcome.DISALLOWED);
        break;
      } else if (allowedMatcher.find()) {
        testOutcome.setOutcome(SpecificationProtos.Outcome.ALLOWED);
        break;
      }
    }
    return testOutcome.build();
  }
}
