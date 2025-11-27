package com.example.backend.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Attribute Converter for PostgreSQL JSONB columns.
 * Converts between Java String and PostgreSQL JSONB type.
 */
@Converter
@Component
public class JsonbConverter implements AttributeConverter<String, Object> {

    private static ObjectMapper objectMapper;

    @Autowired
    public void setObjectMapper(ObjectMapper objectMapper) {
        JsonbConverter.objectMapper = objectMapper;
    }

    @Override
    public Object convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            // Parse the JSON string to ensure it's valid JSON, then return it
            return objectMapper.readTree(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid JSON string: " + attribute, e);
        }
    }

    @Override
    public String convertToEntityAttribute(Object dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dbData);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot convert JSONB data to string", e);
        }
    }
}
