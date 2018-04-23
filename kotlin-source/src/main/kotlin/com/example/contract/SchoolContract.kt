package com.example.contract

import com.example.state.SchoolState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [IOUState], which in turn encapsulates an [IOU].
 *
 * For a new [IOU] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [IOU].
 * - An Create() command with the public keys of both the lender and the borrower.
 *
 * All contracts must sub-class the [Contract] interface.
 */
open class SchoolContract : Contract {
    companion object {
        @JvmStatic
        val School_CONTRACT_ID = "com.example.contract.SchoolContract"
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<SchoolContract.Commands>()
        if(command.value is Commands.Create) {
            requireThat {
                // Generic constraints around the IOU transaction.
                "No inputs should be consumed when creating school." using (tx.inputs.isEmpty())
                "Only one output state should be created." using (tx.outputs.size == 1)
                val out = tx.outputsOfType<SchoolState>().single()
                "The school and deo cannot be the same entity." using (out.school!= out.deo)
                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                // IOU-specific constraints.
                "The student id must be non-negative." using (out.id > 0)
            }
        } else {
            requireThat {
                // Generic constraints around the IOU transaction.
                "Inputs should be consumed when Authorizing a Service Credit." using (tx.inputs.isNotEmpty())

                val out = tx.outputsOfType<SchoolState>().single()

                "All of the participants must be signers." using (command.signers.containsAll(out.participants.map { it.owningKey }))

                // Service Credits-specific constraints.
                "The student id must be non-negative." using (out.id > 0)


            }
        }


    }

    /**
     * This contract only implements one command, Create.
     */
    interface Commands : CommandData {
        class Create : Commands
        class Edit : Commands
    }
}
