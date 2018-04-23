package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.example.contract.SchoolContract
import com.example.schema.SchoolSchemaV1
import com.example.state.SchoolState
import net.corda.core.contracts.Command
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.Requirements.using
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.node.services.vault.QueryCriteria.VaultCustomQueryCriteria






object EditFlow {
    @InitiatingFlow
    @StartableByRPC
    class Editor(val studentId: Int,
                 val age: Int,
                 val deo: Party,
                 val school: Party) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker checkpoints each stage of the flow and outputs the specified messages when each
         * checkpoint is reached in the code. See the 'progressTracker.currentStep' expressions within the call() function.
         */
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on new IOU.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            // Obtain a reference to the notary we want to use.
            val notary = serviceHub.networkMapCache.notaryIdentities[0]

            //Query the vault state by studentId
            val studentIdCriteria = builder { SchoolSchemaV1.PersistentSchool::id.equal(studentId) }
            val customCriteria1 = VaultCustomQueryCriteria(studentIdCriteria)
            val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)

            val vaultState= serviceHub.vaultService.queryBy<SchoolState>(generalCriteria.and(customCriteria1)).states.singleOrNull()

            println("VaultSat=========>"+vaultState)
            val linearId = vaultState!!.state.data.linearId
            val name =  vaultState!!.state.data.name


            // Stage 1.
            progressTracker.currentStep = GENERATING_TRANSACTION
            // Generate an unsigned transaction.
            val schoolState = SchoolState(name,age,studentId,school,deo,linearId)

            val txCommand = Command(SchoolContract.Commands.Create(), schoolState.participants.map { it.owningKey })
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(schoolState, SchoolContract.School_CONTRACT_ID)
                    .addCommand(txCommand)

            //Add vault state
            if (vaultState != null) {
                txBuilder.addInputState(vaultState)
            }


            // Stage 2.
            progressTracker.currentStep = VERIFYING_TRANSACTION
            // Verify that the transaction is valid.
            txBuilder.verify(serviceHub)

            // Stage 3.
            progressTracker.currentStep = SIGNING_TRANSACTION
            // Sign the transaction.
            val partSignedTx = serviceHub.signInitialTransaction(txBuilder)

            // Stage 4.
            progressTracker.currentStep = GATHERING_SIGS
            // Send the state to the counterparty, and receive it back with their signature.
            val otherPartyFlow = initiateFlow(school)
            val fullySignedTx = subFlow(CollectSignaturesFlow(partSignedTx, setOf(otherPartyFlow), GATHERING_SIGS.childProgressTracker()))

            // Stage 5.
            progressTracker.currentStep = FINALISING_TRANSACTION
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(FinalityFlow(fullySignedTx, FINALISING_TRANSACTION.childProgressTracker()))
        }
    }

    @InitiatedBy(Editor::class)
    class Acceptor(val otherPartyFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val signTransactionFlow = object : SignTransactionFlow(otherPartyFlow) {
                override fun checkTransaction(stx: SignedTransaction) = requireThat {
                    val output = stx.tx.outputs.single().data
                    "This must be an IOU transaction." using (output is SchoolState)
                    val student= output as SchoolState
                    "I won't accept id greater than 0" using (student.id >= 0)
                }
            }

            return subFlow(signTransactionFlow)
        }
    }
}