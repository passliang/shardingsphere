/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.sharding.rewrite.parameterized;

import com.google.common.base.Preconditions;
import org.apache.shardingsphere.core.rule.RuleBuilder;
import org.apache.shardingsphere.core.yaml.config.sharding.YamlRootShardingConfiguration;
import org.apache.shardingsphere.core.yaml.constructor.YamlRootShardingConfigurationConstructor;
import org.apache.shardingsphere.core.yaml.swapper.ShardingRuleConfigurationYamlSwapper;
import org.apache.shardingsphere.sql.parser.SQLParserEngine;
import org.apache.shardingsphere.sql.parser.SQLParserEngineFactory;
import org.apache.shardingsphere.sql.parser.binder.metadata.column.ColumnMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.index.IndexMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.schema.SchemaMetaData;
import org.apache.shardingsphere.sql.parser.binder.metadata.table.TableMetaData;
import org.apache.shardingsphere.underlying.common.config.properties.ConfigurationProperties;
import org.apache.shardingsphere.underlying.common.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.underlying.common.metadata.datasource.DataSourceMetas;
import org.apache.shardingsphere.underlying.common.metadata.schema.RuleSchemaMetaData;
import org.apache.shardingsphere.underlying.common.rule.BaseRule;
import org.apache.shardingsphere.underlying.common.yaml.engine.YamlEngine;
import org.apache.shardingsphere.underlying.rewrite.SQLRewriteEntry;
import org.apache.shardingsphere.underlying.rewrite.engine.result.GenericSQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.result.RouteSQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.result.SQLRewriteResult;
import org.apache.shardingsphere.underlying.rewrite.engine.result.SQLRewriteUnit;
import org.apache.shardingsphere.underlying.rewrite.parameterized.engine.AbstractSQLRewriterParameterizedTest;
import org.apache.shardingsphere.underlying.rewrite.parameterized.engine.parameter.SQLRewriteEngineTestParameters;
import org.apache.shardingsphere.underlying.rewrite.parameterized.engine.parameter.SQLRewriteEngineTestParametersBuilder;
import org.apache.shardingsphere.underlying.route.DataNodeRouter;
import org.apache.shardingsphere.underlying.route.context.RouteContext;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.sql.Types;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ShardingSQLRewriterParameterizedTest extends AbstractSQLRewriterParameterizedTest {
    
    private static final String PATH = "sharding";
    
    public ShardingSQLRewriterParameterizedTest(final String type, final String name, final String fileName, final SQLRewriteEngineTestParameters testParameters) {
        super(testParameters);
    }
    
    @Parameters(name = "{0}: {1} -> {2}")
    public static Collection<Object[]> loadTestParameters() {
        return SQLRewriteEngineTestParametersBuilder.loadTestParameters(PATH.toUpperCase(), PATH, ShardingSQLRewriterParameterizedTest.class);
    }
    
    @Override
    protected Collection<SQLRewriteUnit> createSQLRewriteUnits() throws IOException {
        YamlRootShardingConfiguration ruleConfiguration = createRuleConfiguration();
        Collection<BaseRule> rules = RuleBuilder.build(ruleConfiguration.getDataSources().keySet(), new ShardingRuleConfigurationYamlSwapper().swap(ruleConfiguration.getShardingRule()));
        SQLParserEngine sqlParserEngine = SQLParserEngineFactory.getSQLParserEngine(null == getTestParameters().getDatabaseType() ? "SQL92" : getTestParameters().getDatabaseType());
        ShardingSphereMetaData metaData = createShardingSphereMetaData();
        ConfigurationProperties properties = new ConfigurationProperties(ruleConfiguration.getProps());
        RouteContext routeContext = new DataNodeRouter(metaData, properties, rules).route(
                sqlParserEngine.parse(getTestParameters().getInputSQL(), false), getTestParameters().getInputSQL(), getTestParameters().getInputParameters());
        SQLRewriteResult sqlRewriteResult = new SQLRewriteEntry(metaData.getSchema().getConfiguredSchemaMetaData(),
                properties, rules).rewrite(getTestParameters().getInputSQL(), getTestParameters().getInputParameters(), routeContext);
        return sqlRewriteResult instanceof GenericSQLRewriteResult
                ? Collections.singletonList(((GenericSQLRewriteResult) sqlRewriteResult).getSqlRewriteUnit()) : (((RouteSQLRewriteResult) sqlRewriteResult).getSqlRewriteUnits()).values();
    }
    
    private YamlRootShardingConfiguration createRuleConfiguration() throws IOException {
        URL url = ShardingSQLRewriterParameterizedTest.class.getClassLoader().getResource(getTestParameters().getRuleFile());
        Preconditions.checkNotNull(url, "Cannot found rewrite rule yaml configuration.");
        return YamlEngine.unmarshal(new File(url.getFile()), YamlRootShardingConfiguration.class, new YamlRootShardingConfigurationConstructor());
    }
    
    private ShardingSphereMetaData createShardingSphereMetaData() {
        SchemaMetaData schemaMetaData = mock(SchemaMetaData.class);
        when(schemaMetaData.getAllTableNames()).thenReturn(Arrays.asList("t_account", "t_account_detail"));
        TableMetaData accountTableMetaData = mock(TableMetaData.class);
        when(accountTableMetaData.getColumns()).thenReturn(createColumnMetaDataMap());
        Map<String, IndexMetaData> indexMetaDataMap = new HashMap<>(1, 1);
        indexMetaDataMap.put("index_name", new IndexMetaData("index_name"));
        when(accountTableMetaData.getIndexes()).thenReturn(indexMetaDataMap);
        when(schemaMetaData.containsTable("t_account")).thenReturn(true);
        when(schemaMetaData.get("t_account")).thenReturn(accountTableMetaData);
        when(schemaMetaData.get("t_account_detail")).thenReturn(mock(TableMetaData.class));
        when(schemaMetaData.getAllColumnNames("t_account")).thenReturn(Arrays.asList("account_id", "amount", "status"));
        RuleSchemaMetaData ruleSchemaMetaData = mock(RuleSchemaMetaData.class);
        when(ruleSchemaMetaData.getConfiguredSchemaMetaData()).thenReturn(schemaMetaData);
        return new ShardingSphereMetaData(mock(DataSourceMetas.class), ruleSchemaMetaData);
    }
    
    private Map<String, ColumnMetaData> createColumnMetaDataMap() {
        Map<String, ColumnMetaData> result = new LinkedHashMap<>();
        result.put("account_id", new ColumnMetaData("account_id", Types.INTEGER, "INT", true, true, false));
        result.put("amount", mock(ColumnMetaData.class));
        result.put("status", mock(ColumnMetaData.class));
        return result;
    }
}
