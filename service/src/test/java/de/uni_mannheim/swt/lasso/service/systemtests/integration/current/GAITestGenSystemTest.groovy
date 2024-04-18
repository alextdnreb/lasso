/*
 * LASSO - an Observatorium for the Dynamic Selection, Analysis and Comparison of Software
 * Copyright (C) 2024 Marcus Kessel (University of Mannheim) and LASSO contributers
 *
 * This file is part of LASSO.
 *
 * LASSO is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LASSO is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LASSO.  If not, see <https://www.gnu.org/licenses/>.
 */
package de.uni_mannheim.swt.lasso.service.systemtests.integration.current

import de.uni_mannheim.swt.lasso.engine.DataSourceNotFoundException
import de.uni_mannheim.swt.lasso.engine.LSLExecutionContext
import de.uni_mannheim.swt.lasso.engine.LSLExecutionResult
import de.uni_mannheim.swt.lasso.engine.LSLScript
import de.uni_mannheim.swt.lasso.engine.action.utils.DistributedFileSystemUtils
import de.uni_mannheim.swt.lasso.service.systemtests.integration.AbstractGroovySystemTest
import de.uni_mannheim.swt.lasso.service.systemtests.util.LassoTestEngine
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

import static org.junit.jupiter.api.Assertions.assertFalse

/**
 * Demonstrates GAITestGen action.
 *
 * @author mkessel
 */
class GAITestGenSystemTest extends AbstractGroovySystemTest {

    @Autowired
    @Qualifier("testLassoEngine")
    LassoTestEngine lassoEngine;

    @Test
    void testGAITestGen_benchmark_HumanEval_13_greatest_common_divisor() throws IOException, DataSourceNotFoundException {
        @Language("Groovy")
        String content = '''
import de.uni_mannheim.swt.lasso.service.Tester

dataSource 'dummy'

study(name: 'GAITestGen-Tests') {
    action(name: 'select') {
        List systems = [Tester.system("1", "Clazz", "pkg")]
        abstraction(systems, "HumanEval_13_greatest_common_divisor")
    }

    action(name: 'gai', type: 'GAITestGen') {
        apiUrl = "http://bagdana.informatik.uni-mannheim.de:8080/v1/chat/completions"
        apiKey = "swt4321"
        maxNoOfTests = 100
        noOfPrompts = 1
        benchmark = "humaneval-java-reworded"
        
        dependsOn 'select'
        includeAbstractions '*'
    }
}
        '''

        //
        LSLScript scriptUnderTest = createScript(content)

        // DO EXECUTE
        LSLExecutionResult lslExecutionResult = lassoEngine.execute(scriptUnderTest)
        LSLExecutionContext lslExecutionContext = lassoEngine.getLastContext()

        // check DFS
        List<String> sequences = DistributedFileSystemUtils.listSequences(lslExecutionContext, "gai", "HumanEval_13_greatest_common_divisor")
        println(sequences)

        assertFalse(sequences.isEmpty())
    }

    @Test
    void testGAITestGen_benchmark_HumanEval_13_greatest_common_divisor_manual() throws IOException, DataSourceNotFoundException {
        @Language("Groovy")
        String content = '''
import de.uni_mannheim.swt.lasso.core.model.Specification
import de.uni_mannheim.swt.lasso.engine.action.utils.SequenceUtils
import de.uni_mannheim.swt.lasso.service.Tester

dataSource 'dummy'

study(name: 'GAITestGen-Tests') {
    action(name: 'select') {
        List systems = [Tester.system("1", "Clazz", "pkg")]
        Specification spec = SequenceUtils.parseSpecificationFromLQL("Problem{ greatestCommonDivisor(long,long)->long }")
        abstraction(systems, "HumanEval_13_greatest_common_divisor", spec)
    }

    action(name: 'gai', type: 'GAITestGen') {
        apiUrl = "http://bagdana.informatik.uni-mannheim.de:8080/v1/chat/completions"
        apiKey = "swt4321"
        maxNoOfTests = 100
        noOfPrompts = 1
        sequences = [
                'testEncode': sheet(mut:'Problem') {
                    row  '',    'create', '?mut'
                    row 6l,  'greatestCommonDivisor', 'A1', 54l, 24l
                }
        ]
        
        dependsOn 'select'
        includeAbstractions '*'
    }
}
        '''

        //
        LSLScript scriptUnderTest = createScript(content)

        // DO EXECUTE
        LSLExecutionResult lslExecutionResult = lassoEngine.execute(scriptUnderTest)
        LSLExecutionContext lslExecutionContext = lassoEngine.getLastContext()

        // check DFS
        List<String> sequences = DistributedFileSystemUtils.listSequences(lslExecutionContext, "gai", "HumanEval_13_greatest_common_divisor")
        println(sequences)

        assertFalse(sequences.isEmpty())
    }
}
