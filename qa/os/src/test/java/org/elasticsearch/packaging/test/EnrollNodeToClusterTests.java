/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.packaging.test;

import org.elasticsearch.Version;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.packaging.util.Archives;
import org.elasticsearch.packaging.util.Distribution;
import org.elasticsearch.packaging.util.Platforms;
import org.elasticsearch.packaging.util.Shell;
import org.elasticsearch.xpack.core.security.EnrollmentToken;
import org.junit.BeforeClass;

import java.util.List;

import static org.elasticsearch.packaging.util.Archives.installArchive;
import static org.elasticsearch.packaging.util.Archives.verifyArchiveInstallation;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assume.assumeTrue;

public class EnrollNodeToClusterTests extends PackagingTestCase {

    @BeforeClass
    public static void filterDistros() {
        assumeTrue("only archives", distribution.isArchive());
    }

    public void test10Install() throws Exception {
        installation = installArchive(sh, distribution());
        verifyArchiveInstallation(installation, distribution());
    }

    public void test20EnrollToClusterWithEmptyTokenValue() throws Exception {
        Shell.Result result = Archives.runElasticsearchStartCommand(installation, sh, null, List.of("--enrollment-token"), false);
        // something in our tests wrap the error code to 1 on windows
        // TODO investigate this and remove this guard
        if (distribution.platform != Distribution.Platform.WINDOWS) {
            assertThat(result.exitCode, equalTo(ExitCodes.USAGE));
        }
        verifySecurityNotAutoConfigured(installation);
    }

    public void test30EnrollToClusterWithInvalidToken() throws Exception {
        Shell.Result result = Archives.runElasticsearchStartCommand(
            installation,
            sh,
            null,
            List.of("--enrollment-token", "somerandomcharsthatarenotabase64encodedjsonstructure"),
            false
        );
        // something in our tests wrap the error code to 1 on windows
        // TODO investigate this and remove this guard
        if (distribution.platform != Distribution.Platform.WINDOWS) {
            assertThat(result.exitCode, equalTo(ExitCodes.DATA_ERROR));
        }
        verifySecurityNotAutoConfigured(installation);
    }

    public void test40EnrollmentFailsForConfiguredNode() throws Exception {
        // auto-config requires that the archive owner and the process user be the same,
        Platforms.onWindows(() -> sh.chown(installation.config, installation.getOwner()));
        startElasticsearch();
        verifySecurityAutoConfigured(installation);
        stopElasticsearch();
        Shell.Result result = Archives.runElasticsearchStartCommand(
            installation,
            sh,
            null,
            List.of("--enrollment-token", generateMockEnrollmentToken()),
            false
        );
        // something in our tests wrap the error code to 1 on windows
        // TODO investigate this and remove this guard
        if (distribution.platform != Distribution.Platform.WINDOWS) {
            assertThat(result.exitCode, equalTo(ExitCodes.NOOP));
        }
        Platforms.onWindows(() -> sh.chown(installation.config));
    }

    public void test50MultipleValuesForEnrollmentToken() throws Exception {
        // if invoked with --enrollment-token tokenA tokenB tokenC, only tokenA is read
        Shell.Result result = Archives.runElasticsearchStartCommand(
            installation,
            sh,
            null,
            List.of("--enrollment-token", generateMockEnrollmentToken(), "some-other-token", "some-other-token", "some-other-token"),
            false
        );
        // Assert we used the first value which is a proper enrollment token but failed because the node is already configured ( 80 )
        // something in our tests wrap the error code to 1 on windows
        // TODO investigate this and remove this guard
        if (distribution.platform != Distribution.Platform.WINDOWS) {
            assertThat(result.exitCode, equalTo(ExitCodes.NOOP));
        }
    }

    public void test60MultipleParametersForEnrollmentTokenAreNotAllowed() throws Exception {
        // if invoked with --enrollment-token tokenA --enrollment-token tokenB --enrollment-token tokenC, we exit
        Shell.Result result = Archives.runElasticsearchStartCommand(
            installation,
            sh,
            null,
            List.of(
                "--enrollment-token",
                "some-other-token",
                "--enrollment-token",
                "some-other-token",
                "--enrollment-token",
                generateMockEnrollmentToken()
            ),
            false
        );
        assertThat(result.stderr, containsString("Multiple --enrollment-token parameters are not allowed"));
        assertThat(result.exitCode, equalTo(1));
    }

    private String generateMockEnrollmentToken() throws Exception {
        EnrollmentToken enrollmentToken = new EnrollmentToken(
            "some-api-key",
            "e8864fa9cb5a8053ea84a48581a6c9bef619f8f6aaa58a632aac3e0a25d43ea9",
            Version.CURRENT.toString(),
            List.of("localhost:9200")
        );
        return enrollmentToken.getEncoded();
    }
}
