/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.Module
import spock.lang.Unroll

abstract class ComponentMetadataRulesDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def resolve = new ResolveTestFixture(buildFile)
    Module moduleA, moduleB

    abstract HttpRepository getRepo()

    abstract String getRepoDeclaration()

    def setup() {
        resolve.prepare()
        moduleA = repo.module("org.test", "moduleA").allowAll().publish()
        moduleB = repo.module("org.test", "moduleB").allowAll().publish()

        settingsFile << """
            rootProject.name = 'testproject'
        """
        buildFile << """
            $repoDeclaration
            
            configurations { compile }
            
            dependencies {
                compile 'org.test:moduleA:1.0'
            }
        """
    }

    def dependsOn(Module module, Module dependency) {
        module.dependsOn(dependency).publish()
    }

    @Unroll
    def "a dependency can be added using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                components {
                    all { 
                        dependencies.add $dependendy
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:moduleA:1.0') {
                    module('org.test:moduleB:1.0')
                }
            }
        }

        where:
        notation | dependendy
        "string" | "'org.test:moduleB:1.0'"
        "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    @Unroll
    def "a dependency can be added and configured using #notation notation"() {
        when:
        buildFile << """
            dependencies {
                components {
                    all { 
                        dependencies.add($dependendy) {
                            changing = true
                            force = true
                            transitive = true
                        }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:moduleA:1.0') {
                    module('org.test:moduleB:1.0') {
                        module('org.test:moduleB:1.0') //transitive = true
                    }
                }
            }
        }

        where:
        notation | dependendy
        "string" | "'org.test:moduleB:1.0'"
        "map"    | "group: 'org.test', name: 'moduleB', version: '1.0'"
    }

    def "a dependency can be removed"() {
        given:
        dependsOn(moduleA, moduleB)

        when:
        buildFile << """
            dependencies {
                components {
                    all {
                        dependencies.removeAll { it.version == '1.0' }
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:moduleA:1.0')
            }
        }
    }

    def "dependency modifications are visible in the next rule"() {
        when:
        buildFile << """
            dependencies {
                components {
                    all { 
                        assert dependencies.size() == 0
                        dependencies.add 'org.test:moduleB:1.0'
                    }
                    all {
                        assert dependencies.size() == 1
                        dependencies.removeAll { true }
                    }
                    all {
                        assert dependencies.size() == 0
                    }
                }
            }
        """

        then:
        succeeds 'checkDep'
        resolve.expectGraph {
            root(':', ':testproject:') {
                module('org.test:moduleA:1.0')
            }
        }
    }
}
