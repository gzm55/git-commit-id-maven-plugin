/*
 * This file is part of git-commit-id-maven-plugin by Konrad 'ktoso' Malawski <konrad.malawski@java.pl>
 *
 * git-commit-id-maven-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * git-commit-id-maven-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with git-commit-id-maven-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.project13.maven.git;

import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import pl.project13.core.log.LoggerBridge;

import java.util.*;
import java.util.regex.Pattern;

/**
 * This class encapsulates logic to perform property replacements.
 * For a use-case refer to https://github.com/git-commit-id/git-commit-id-maven-plugin/issues/317.
 */
public class PropertiesReplacer {
  private final LoggerBridge log;
  private final PluginParameterExpressionEvaluator expressionEvaluator;

  /**
   * Constructor to encapsulates all references required to perform property replacements.
   * @param log The logger to log any messages
   * @param expressionEvaluator Maven's PluginParameterExpressionEvaluator
   *                            (see https://github.com/git-commit-id/git-commit-id-maven-plugin/issues/413 why it's needed)
   */
  public PropertiesReplacer(LoggerBridge log, PluginParameterExpressionEvaluator expressionEvaluator) {
    this.log = log;
    this.expressionEvaluator = expressionEvaluator;
  }

  /**
   * Method that performs the actual property replacement.
   * @param properties all properties that are being generated by the plugin
   * @param replacementProperties list of all replacement actions to perform
   */
  public void performReplacement(Properties properties, List<ReplacementProperty> replacementProperties) {
    if ((replacementProperties != null) && (properties != null)) {
      for (ReplacementProperty replacementProperty: replacementProperties) {
        String propertyKey = replacementProperty.getProperty();
        if (propertyKey == null) {
          performReplacementOnAllGeneratedProperties(properties, replacementProperty);
        } else {
          performReplacementOnSingleProperty(properties, replacementProperty, propertyKey);
        }
      }
    }
  }

  private void performReplacementOnAllGeneratedProperties(Properties properties, ReplacementProperty replacementProperty) {
    for (String propertyName : properties.stringPropertyNames()) {
      String content = properties.getProperty(propertyName);
      String result = performReplacement(replacementProperty, content);
      if ((replacementProperty.getPropertyOutputSuffix() != null) && (!replacementProperty.getPropertyOutputSuffix().isEmpty())) {
        String newPropertyKey = propertyName + "." + replacementProperty.getPropertyOutputSuffix();
        properties.setProperty(newPropertyKey, result);
        log.info("apply replace on property " + propertyName + " and save to " + newPropertyKey + ": original value '" + content + "' with '" + result + "'");
      } else {
        properties.setProperty(propertyName, result);
        log.info("apply replace on property " + propertyName + ": original value '" + content + "' with '" + result + "'");
      }
    }
  }

  private void performReplacementOnSingleProperty(Properties properties, ReplacementProperty replacementProperty, String propertyKey) {
    String content = properties.getProperty(propertyKey);
    String result = performReplacement(replacementProperty, content);
    if ((replacementProperty.getPropertyOutputSuffix() != null) && (!replacementProperty.getPropertyOutputSuffix().isEmpty())) {
      String newPropertyKey = propertyKey + "." + replacementProperty.getPropertyOutputSuffix();
      properties.setProperty(newPropertyKey, result);
      log.info("apply replace on property " + propertyKey + " and save to " + newPropertyKey + ": original value '" + content + "' with '" + result + "'");
    } else {
      properties.setProperty(propertyKey, result);
      log.info("apply replace on property " + propertyKey + ": original value '" + content + "' with '" + result + "'");
    }
  }

  private String performReplacement(ReplacementProperty replacementProperty, String content) {
    String evaluationContent = content;
    if (evaluationContent == null || evaluationContent.isEmpty() || replacementProperty.isForceValueEvaluation()) {
      evaluationContent = replacementProperty.getValue();
    }
    String result = "";
    try {
      result = Optional
              .ofNullable(expressionEvaluator.evaluate(evaluationContent))
              .map(x -> x.toString()).orElse(evaluationContent);
    } catch (Exception e) {
      log.error("Something went wrong performing the replacement.", e);
    }
    if (replacementProperty != null) {
      result = performTransformationRules(replacementProperty, result, TransformationRule.ApplyEnum.BEFORE);
      if (replacementProperty.isRegex()) {
        result = replaceRegex(result, replacementProperty.getToken(), replacementProperty.getValue());
      } else {
        result = replaceNonRegex(result, replacementProperty.getToken(), replacementProperty.getValue());
      }
      result = performTransformationRules(replacementProperty, result, TransformationRule.ApplyEnum.AFTER);
    }
    return result;
  }

  private String performTransformationRules(ReplacementProperty replacementProperty, String content, TransformationRule.ApplyEnum forRule) {
    String result = content;
    if ((replacementProperty.getTransformationRules() != null) && (!replacementProperty.getTransformationRules().isEmpty())) {
      for (TransformationRule transformationRule: replacementProperty.getTransformationRules()) {
        if (transformationRule.getApplyRule().equals(forRule)) {
          result = transformationRule.getActionRule().perform(result);
        }
      }
    }
    return result;
  }

  private String replaceRegex(String content, String token, String value) {
    if (token == null) {
      log.error("found replacementProperty without required token.");
      return content;
    }
    final Pattern compiledPattern = Pattern.compile(token);
    return compiledPattern.matcher(content).replaceAll(value == null ? "" : value);
  }

  private String replaceNonRegex(String content, String token, String value) {
    if (token == null) {
      log.error("found replacementProperty without required token.");
      return content;
    }
    return content.replace(token, value == null ? "" : value);
  }
}
