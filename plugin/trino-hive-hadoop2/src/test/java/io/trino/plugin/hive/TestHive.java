/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.trino.plugin.hive;

import org.apache.hadoop.net.NetUtils;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestHive
        extends AbstractTestHive
{
    private int hiveVersionMajor;

    @Parameters({
            "hive.hadoop2.metastoreHost",
            "hive.hadoop2.metastorePort",
            "hive.hadoop2.databaseName",
            "hive.hadoop2.hiveVersionMajor",
            "hive.hadoop2.timeZone",
    })
    @BeforeClass
    public void initialize(String host, int port, String databaseName, int hiveVersionMajor, String timeZone)
    {
        String hadoopMasterIp = System.getProperty("hadoop-master-ip");
        if (hadoopMasterIp != null) {
            // Even though Hadoop is accessed by proxy, Hadoop still tries to resolve hadoop-master
            // (e.g: in: NameNodeProxies.createProxy)
            // This adds a static resolution for hadoop-master to docker container internal ip
            NetUtils.addStaticResolution("hadoop-master", hadoopMasterIp);
        }

        checkArgument(hiveVersionMajor > 0, "Invalid hiveVersionMajor: %s", hiveVersionMajor);
        setup(host, port, databaseName, hiveVersionMajor >= 3 ? "UTC" : timeZone);

        this.hiveVersionMajor = hiveVersionMajor;
    }

    private int getHiveVersionMajor()
    {
        checkState(hiveVersionMajor > 0, "hiveVersionMajor not set");
        return hiveVersionMajor;
    }

    @Override
    public void testGetPartitionSplitsTableOfflinePartition()
    {
        if (getHiveVersionMajor() >= 2) {
            throw new SkipException("ALTER TABLE .. ENABLE OFFLINE was removed in Hive 2.0 and this is a prerequisite for this test");
        }

        super.testGetPartitionSplitsTableOfflinePartition();
    }

    @Override
    public void testHideDeltaLakeTables()
    {
        assertThatThrownBy(super::testHideDeltaLakeTables)
                .hasMessageMatching("(?s)\n" +
                        "Expecting\n" +
                        " <\\[.*\\b(\\w+.tmp_presto_test_presto_delta_lake_table_\\w+)\\b.*]>\n" +
                        "not to contain\n" +
                        " <\\[\\1]>\n" +
                        "but found.*");

        throw new SkipException("not supported");
    }

    @Test
    public void testHiveViewTranslationError()
    {
        try (Transaction transaction = newTransaction()) {
            assertThatThrownBy(() -> transaction.getMetadata().getView(newSession(), view))
                    .isInstanceOf(HiveViewNotSupportedException.class)
                    .hasMessageContaining("Hive views are not supported");

            // TODO: combine this with tests for successful translation (currently in TestHiveViews product test)
        }
    }
}