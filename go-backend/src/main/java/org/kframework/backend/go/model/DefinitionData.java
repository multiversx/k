// Copyright (c) 2015-2019 K Team. All Rights Reserved.
package org.kframework.backend.go.model;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.SetMultimap;
import org.kframework.backend.go.gopackage.GoExternalHookManager;
import org.kframework.definition.Module;
import org.kframework.definition.Rule;
import org.kframework.kore.KLabel;

import java.util.Map;
import java.util.Set;

public class DefinitionData {

    public final Module mainModule;
    public final Set<KLabel> functions;
    public final Set<KLabel> anywhereKLabels;
    public final SetMultimap<KLabel, Rule> functionRules;
    public final ListMultimap<KLabel, Rule> anywhereRules;
    public final Map<KLabel, FunctionInfo> functionInfoMap;
    public final KLabel topCellInitializer;
    public final Map<KLabel, KLabel> collectionFor;
    public final ConstantKTokens constants;
    public final GoExternalHookManager extHookManager;
    public final Rule makeStuck, makeUnstuck;

    public DefinitionData(
            Module mainModule,
            Set<KLabel> functions,
            Set<KLabel> anywhereKLabels,
            SetMultimap<KLabel, Rule> functionRules,
            ListMultimap<KLabel, Rule> anywhereRules,
            Map<KLabel, FunctionInfo> functionInfoMap,
            KLabel topCellInitializer,
            Map<KLabel, KLabel> collectionFor,
            ConstantKTokens constants,
            GoExternalHookManager extHookManager,
            Rule makeStuck, Rule makeUnstuck) {
        this.mainModule = mainModule;
        this.functions = functions;
        this.anywhereKLabels = anywhereKLabels;
        this.functionRules = functionRules;
        this.anywhereRules = anywhereRules;
        this.functionInfoMap = functionInfoMap;
        this.topCellInitializer = topCellInitializer;
        this.collectionFor = collectionFor;
        this.constants = constants;
        this.extHookManager = extHookManager;
        this.makeStuck = makeStuck;
        this.makeUnstuck = makeUnstuck;

    }

    public boolean isFunctionOrAnywhere(KLabel klabel) {
        return functions.contains(klabel) || anywhereKLabels.contains(klabel);
    }
}
