package com.entel.migration.schema;

import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Definicion de la tabla {@link TableNames#ROLE_REPOSITORY_MENU}: PK por
 * tipo de menu (S/I), SK por codigo de orden. Cubre el Patron 5
 * ({@code MenuRepository.findTipo}) del diseno canonico
 * (docs/architecture.md). Sin GSI: el filtro por tipoUsuario y la
 * jerarquia por cod_submenu ocurren en la capa de aplicacion.
 */
public final class RoleRepositoryMenuTableDefinition {

    private RoleRepositoryMenuTableDefinition() {
    }

    public static CreateTableRequest createTableRequest() {
        return CreateTableRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY_MENU)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.SK)
                                .attributeType(ScalarAttributeType.S)
                                .build())
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName(ItemAttributes.PK)
                                .keyType(KeyType.HASH)
                                .build(),
                        KeySchemaElement.builder()
                                .attributeName(ItemAttributes.SK)
                                .keyType(KeyType.RANGE)
                                .build())
                .build();
    }
}
