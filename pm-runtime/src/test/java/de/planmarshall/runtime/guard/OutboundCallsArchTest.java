/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.JarURLConnection;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.role.ShippedRoles;
import de.planmarshall.runtime.corpus.HuggingFaceSourceRepository;
import de.planmarshall.runtime.guard.fixtures.ChannelFixture;
import de.planmarshall.runtime.guard.fixtures.ClientSocketFixture;
import de.planmarshall.runtime.guard.fixtures.DataTypesFixture;
import de.planmarshall.runtime.guard.fixtures.DatagramFixture;
import de.planmarshall.runtime.guard.fixtures.HttpClientFixture;
import de.planmarshall.runtime.guard.fixtures.ListeningOnlyFixture;
import de.planmarshall.runtime.guard.fixtures.SocketFactoryFixture;
import de.planmarshall.runtime.guard.fixtures.UrlConnectionFixture;
import de.planmarshall.runtime.job.WorkerEgressProxy;

/**
 * Guards the closed list of outbound calls of the runtime (rule A): no class of {@code pm-runtime} or
 * {@code pm-core} opens a connection of its own ({@code PM-ARCH-2}, {@code PM-TEST-5}).
 * <p>
 * The hazard: "local, no telemetry" holds only while every connection leaves through a class the closed list of
 * Credentials &amp; Enrolment § Closed List of Outbound Calls names. A class that opens a socket, an HTTP client or a
 * URL connection on its own carries an outbound call that no list, no consent and no review covers, and nothing in the
 * build would notice it.
 * <p>
 * The rule fails when a class of the two modules depends on a connection-opening type or calls a connection-opening
 * method. The set, in the words of the specification:
 * <ul>
 *   <li>the HTTP client and web socket of the JDK ({@link HttpClient}, {@link WebSocket});</li>
 *   <li>the client sockets ({@link Socket}, {@link DatagramSocket}, {@link MulticastSocket});</li>
 *   <li>the URL connections ({@link URLConnection} and its subtypes {@link HttpURLConnection},
 *       {@link JarURLConnection}, {@link HttpsURLConnection}) and the calls {@code URL.openConnection} and
 *       {@code URL.openStream};</li>
 *   <li>the socket factories ({@link SocketFactory} and its SSL subtype {@link SSLSocketFactory});</li>
 *   <li>the client channels of NIO ({@link SocketChannel}, {@link DatagramChannel},
 *       {@link AsynchronousSocketChannel}).</li>
 * </ul>
 * Listening types ({@code ServerSocket}, {@code ServerSocketChannel}, {@code AsynchronousServerSocketChannel},
 * {@code com.sun.net.httpserver.HttpServer}) accept connections and open none, so they are not in the set; and
 * {@code java.net.http.HttpRequest}, {@code HttpResponse} and {@code HttpTimeoutException} are data types of the
 * client's API, not connections.
 * <p>
 * The exemptions are a closed map from top-level class to the reason the specification gives it, none by package.
 * A test evaluates the rule without its exemptions over the same import and asserts that the violating classes are
 * exactly the exempt ones, so an entry that no longer violates fails as stale and a violation without an entry fails
 * as unlisted. The import is asserted non-vacuous, so a changed import path cannot make the rule pass on nothing.
 */
// Every datum in this class is a contract: package names, type names, class names, reasons and fixture names.
@DisplayName("The closed list of outbound calls of the runtime")
class OutboundCallsArchTest {

    /** The packages the rule imports: the main classes of the runtime and of the foundation on its test classpath. */
    private static final List<String> MAIN_PACKAGES = List.of("de.planmarshall.runtime", "de.planmarshall.core");

    /** The connection-opening types of the set, with the subtypes of the URL connections and socket factories. */
    private static final List<Class<?>> CONNECTION_TYPES = List.of(HttpClient.class, WebSocket.class, Socket.class,
            DatagramSocket.class, MulticastSocket.class, URLConnection.class, HttpURLConnection.class,
            JarURLConnection.class, HttpsURLConnection.class, SocketFactory.class, SSLSocketFactory.class,
            SocketChannel.class, DatagramChannel.class, AsynchronousSocketChannel.class);

    /** The connection-opening calls of the set, on {@link java.net.URL}. */
    private static final String URL_TYPE = "java.net.URL";

    private static final Set<String> CONNECTION_CALLS = Set.of("openConnection", "openStream");

    /**
     * The one named exemption of rule A per top-level class, with the reason the specification gives it. Nested and
     * anonymous classes of an entry are exempt with it; a package is never exempt.
     */
    private static final Map<String, String> EXEMPTIONS = Map.of(
            "de.planmarshall.runtime.job.WorkerEgressProxy",
            "opens a Socket to the destination of a model job; a model job's own traffic is the egress proxy's "
                    + "(PM-SEC-5) and is not part of the closed list",
            "de.planmarshall.runtime.credentials.dbus.DbusConnection",
            "opens a SocketChannel on a Unix-domain socket, the session bus of the Secret Service, a local IPC that "
                    + "leaves no machine",
            "de.planmarshall.core.role.ShippedRoles",
            "reads a jar: classpath resource through URL.openConnection() and a JarURLConnection, from the JAR and "
                    + "never from a network",
            "de.planmarshall.core.profile.ShippedProfiles",
            "reads a jar: classpath resource through URL.openConnection() and a JarURLConnection, from the JAR and "
                    + "never from a network");

    /** Rule A as it holds for the main classes: a class of the exemption map is not checked. */
    private static final ArchCondition<JavaClass> NO_CONNECTION = connectionFree(false);

    /** Rule A without its exemptions: the evaluation by which the exemption map is proved load-bearing. */
    private static final ArchCondition<JavaClass> NO_CONNECTION_UNEXEMPTED = connectionFree(true);

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(MAIN_PACKAGES.toArray(String[]::new));
    }

    private static ArchCondition<JavaClass> connectionFree(boolean withoutExemptions) {
        return new ArchCondition<>("open no connection of its own") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                if (!withoutExemptions && isExempt(javaClass)) {
                    return;
                }
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> isConnectionType(dependency.getTargetClass()))
                        .forEach(dependency -> events.add(
                                SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                javaClass.getMethodCallsFromSelf().stream()
                        .filter(OutboundCallsArchTest::isConnectionOpeningCall)
                        .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
            }
        };
    }

    private static boolean isExempt(JavaClass javaClass) {
        return EXEMPTIONS.containsKey(topLevelName(javaClass));
    }

    /** The name of the top-level class: nested and anonymous classes map to the class that declares them. */
    private static String topLevelName(JavaClass javaClass) {
        var name = javaClass.getName();
        var nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static boolean isConnectionType(JavaClass target) {
        return CONNECTION_TYPES.stream().anyMatch(target::isAssignableTo);
    }

    private static boolean isConnectionOpeningCall(JavaMethodCall call) {
        return URL_TYPE.equals(call.getTargetOwner().getName()) && CONNECTION_CALLS.contains(call.getName());
    }

    private static boolean opensConnection(JavaClass javaClass) {
        return javaClass.getDirectDependenciesFromSelf().stream()
                .anyMatch(dependency -> isConnectionType(dependency.getTargetClass()))
                || javaClass.getMethodCallsFromSelf().stream().anyMatch(OutboundCallsArchTest::isConnectionOpeningCall);
    }

    private static Set<String> connectionViolators() {
        return mainClasses.stream()
                .filter(OutboundCallsArchTest::opensConnection)
                .map(OutboundCallsArchTest::topLevelName)
                .collect(Collectors.toSet());
    }

    private static EvaluationResult evaluate(Class<?> fixture) {
        return classes().should(NO_CONNECTION_UNEXEMPTED).evaluate(new ClassFileImporter().importClasses(fixture));
    }

    @Nested
    @DisplayName("rule A on the main classes of pm-runtime and pm-core")
    class MainClasses {

        @Test
        @DisplayName("the rule holds, and the import is not empty")
        void ruleHolds() {
            var nonVacuous = mainClasses.contain(HuggingFaceSourceRepository.class)
                    && mainClasses.contain(WorkerEgressProxy.class)
                    && mainClasses.contain(ShippedRoles.class);

            assertTrue(nonVacuous, "The import contains both modules' classes");
            classes().should(NO_CONNECTION).check(mainClasses);
        }

        @Test
        @DisplayName("without the exemptions the rule fails on exactly the exempt classes")
        void exemptionsAreLoadBearing() {
            assertEquals(EXEMPTIONS.keySet(), connectionViolators(), "Only the exempt classes may connect");
        }
    }

    @Nested
    @DisplayName("rule A on the control fixtures")
    class ConnectionControls {

        static Stream<Arguments> fixtures() {
            return Stream.of(
                    Arguments.of(HttpClientFixture.class, "java.net.http.HttpClient"),
                    Arguments.of(ClientSocketFixture.class, "java.net.Socket"),
                    Arguments.of(DatagramFixture.class, "java.net.DatagramSocket"),
                    Arguments.of(ChannelFixture.class, "java.nio.channels.SocketChannel"),
                    Arguments.of(ChannelFixture.class, "java.nio.channels.DatagramChannel"),
                    Arguments.of(ChannelFixture.class, "java.nio.channels.AsynchronousSocketChannel"),
                    Arguments.of(UrlConnectionFixture.class, "java.net.URL.openConnection"),
                    Arguments.of(UrlConnectionFixture.class, "java.net.URL.openStream"),
                    Arguments.of(SocketFactoryFixture.class, "javax.net.ssl.SSLSocketFactory"));
        }

        @ParameterizedTest(name = "{0}: {1}")
        @MethodSource("fixtures")
        @DisplayName("fails on the fixture, naming the forbidden target")
        void failsOnFixture(Class<?> fixture, String forbiddenTarget) {
            var result = evaluate(fixture);

            assertTrue(result.hasViolation(), () -> "The rule fails on " + fixture.getSimpleName());
            assertTrue(result.getFailureReport().getDetails().stream().anyMatch(detail -> detail.contains(forbiddenTarget)),
                    () -> result.getFailureReport().toString());
        }

        @Test
        @DisplayName("holds on a listening socket and a listening HTTP server")
        void holdsOnListeningOnly() {
            var result = evaluate(ListeningOnlyFixture.class);

            assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
        }

        @Test
        @DisplayName("holds on the data types of the HTTP client's API")
        void holdsOnDataTypes() {
            var result = evaluate(DataTypesFixture.class);

            assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
        }
    }
}
