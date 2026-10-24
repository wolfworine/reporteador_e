package com.entel.migration.schema;

import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import java.util.List;

/**
 * Definicion de la tabla {@link TableNames#ROLE_REPOSITORY}: PK/SK
 * primarias mas GSI1 (PK=aplicacion+area, SK=cargo+rol), que cubre los
 * patrones 1, 2, 2b, 3 y 4 del diseno canonico (docs/architecture.md).
 */
public final class RoleRepositoryTableDefinition {

    private RoleRepositoryTableDefinition() {
    }

    public static CreateTableRequest createTableRequest() {
        return CreateTableRequest.builder()
                .tableName(TableNames.ROLE_REPOSITORY)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.SK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.GSI1_PK)
                                .attributeType(ScalarAttributeType.S)
                                .build(),
                        AttributeDefinition.builder()
                                .attributeName(ItemAttributes.GSI1_SK)
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
                .globalSecondaryIndexes(List.of(
                        GlobalSecondaryIndex.builder()
                                .indexName(RoleRepositoryKeys.GSI1_NAME)
                                .keySchema(
                                        KeySchemaElement.builder()
                                                .attributeName(ItemAttributes.GSI1_PK)
                                                .keyType(KeyType.HASH)
                                                .build(),
                                        KeySchemaElement.builder()
                                                .attributeName(ItemAttributes.GSI1_SK)
                                                .keyType(KeyType.RANGE)
                                                .build())
                                .projection(Projection.builder()
                                        .projectionType(ProjectionType.ALL)
                                        .build())
                                .build()))
                .build();
    }
}
