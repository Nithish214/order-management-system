package com.learn.orderservice.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Map;

// Lets Product#specs be a plain Map<String, String> everywhere in Java, while the column
// itself is just TEXT holding a JSON object -- same "raw JSON in a text column" storage
// choice OutboxEvent's own payload column already makes (see V1's migration), just with
// the JSON<->Map translation handled transparently here via JPA's converter mechanism
// instead of manual parsing scattered wherever the column is read. No native Postgres
// JSONB type mapping involved, deliberately -- that needs Hibernate-version-specific
// configuration this project doesn't otherwise use anywhere; a converter over plain TEXT
// works the same on any JPA provider and needs zero extra setup.
//
// Jackson deserializes a JSON object into a Map<String, String> as a LinkedHashMap by
// default, which is what actually preserves spec display order (Author before Format
// before Pages, say) -- not something this class has to arrange itself.
@Converter
public class SpecsConverter implements AttributeConverter<Map<String, String>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, String>> SPECS_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize product specs", e);
        }
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(dbData, SPECS_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not deserialize product specs", e);
        }
    }
}
