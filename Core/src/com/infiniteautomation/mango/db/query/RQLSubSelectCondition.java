/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.infiniteautomation.mango.db.query;

import org.jooq.Condition;

import net.jazdw.rql.parser.ASTNode;

/**
 * Allows creating a condition based on an ASTNode during RQL visitation
 *
 * @author Terry Packer
 */
@FunctionalInterface
public interface RQLSubSelectCondition {
    /**
     * Create a condition for the source table using an AST node for this operation.
     *
     * @param operation
     * @param node
     * @return
     */
    Condition createCondition(RQLOperation operation, ASTNode node);
}
