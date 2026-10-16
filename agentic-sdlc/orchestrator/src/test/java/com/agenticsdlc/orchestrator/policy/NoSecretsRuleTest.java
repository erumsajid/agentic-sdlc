package com.agenticsdlc.orchestrator.policy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoSecretsRuleTest {
    @Test
    void detectsCredentialAssignmentsWithoutReturningTheSecret() {
        assertThat(NoSecretsRule.containsSecret("configure password=hunter2hunter2 for postgres")).isTrue();
        assertThat(NoSecretsRule.containsSecret("ordinary database configuration")).isFalse();
        assertThat(NoSecretsRule.containsSecret(null)).isFalse();
    }

    @Test
    void detectsPrivateKeysAndAwsAccessKeyIds() {
        assertThat(NoSecretsRule.containsSecret("-----BEGIN PRIVATE KEY-----")).isTrue();
        assertThat(NoSecretsRule.containsSecret("AKIA1234567890ABCDEF")).isTrue();
    }
}
