package com.reydark.reycom.repository;

import com.reydark.reycom.dynamodb.OrderEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;

@Repository
@ConditionalOnProperty(prefix = "aws.dynamodb", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventDynamoRepository {

    private final DynamoDbTable<OrderEvent> table;

    public OrderEventDynamoRepository(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.table-name}") String tableName
    ) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderEvent.class));
    }

    public void save(OrderEvent event) {
        table.putItem(event);
    }

    public List<OrderEvent> findByOrderId(String orderId) {
        Key key = Key.builder()
                .partitionValue(orderId)
                .build();

        return table.query(QueryConditional.keyEqualTo(key))
                .items()
                .stream()
                .toList();
    }
}
