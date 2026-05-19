package io.openaev.engine;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.engine.model.log.LogEvent;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the search engine mapping for the audit-log index by reflecting on {@link LogEvent} and
 * its nested {@link LogEvent.UserMetadata} class.
 *
 * <p>Mapping rules:
 *
 * <ul>
 *   <li>{@code String} fields → {@code keyword}
 *   <li>{@code Instant} fields → {@code date}
 *   <li>{@code LogEvent.UserMetadata} field → {@code object} with recursively generated
 *       sub-properties
 *   <li>{@code Map<String, Object>} field → {@code object} with {@code dynamic: true}
 * </ul>
 *
 * <p>Field names are resolved using the {@link JsonProperty} annotation when present, falling back
 * to the Java field name.
 */
public final class AuditLogMappingBuilder {

  /** Marker interface for field type classification used by driver-specific mapping builders. */
  public enum FieldType {
    KEYWORD,
    DATE,
    OBJECT_NESTED,
    OBJECT_DYNAMIC
  }

  /** Represents a single mapped field with its resolved name, type, and optional sub-fields. */
  public record MappedField(String name, FieldType type, Map<String, MappedField> children) {
    public MappedField(String name, FieldType type) {
      this(name, type, Map.of());
    }
  }

  private AuditLogMappingBuilder() {}

  /**
   * Builds a driver-agnostic mapping description for the audit-log index.
   *
   * @return a map of field name → {@link MappedField} descriptors
   */
  public static Map<String, MappedField> buildMapping() {
    Map<String, MappedField> fields = new HashMap<>();
    for (Field field : LogEvent.class.getDeclaredFields()) {
      String fieldName = resolveJsonFieldName(field);
      Class<?> fieldType = field.getType();

      if (fieldType == String.class) {
        fields.put(fieldName, new MappedField(fieldName, FieldType.KEYWORD));
      } else if (fieldType == Instant.class) {
        fields.put(fieldName, new MappedField(fieldName, FieldType.DATE));
      } else if (fieldType == LogEvent.UserMetadata.class) {
        Map<String, MappedField> nested = new HashMap<>();
        for (Field nestedField : LogEvent.UserMetadata.class.getDeclaredFields()) {
          String nestedName = resolveJsonFieldName(nestedField);
          Class<?> nestedType = nestedField.getType();
          if (nestedType == String.class) {
            nested.put(nestedName, new MappedField(nestedName, FieldType.KEYWORD));
          } else if (nestedType == Instant.class) {
            nested.put(nestedName, new MappedField(nestedName, FieldType.DATE));
          }
        }
        fields.put(fieldName, new MappedField(fieldName, FieldType.OBJECT_NESTED, nested));
      } else if (Map.class.isAssignableFrom(fieldType)) {
        fields.put(fieldName, new MappedField(fieldName, FieldType.OBJECT_DYNAMIC));
      }
    }
    return fields;
  }

  /** Resolves the JSON field name using {@link JsonProperty} annotation or the field name. */
  private static String resolveJsonFieldName(Field field) {
    JsonProperty annotation = field.getAnnotation(JsonProperty.class);
    return (annotation != null && !annotation.value().isEmpty())
        ? annotation.value()
        : field.getName();
  }
}
