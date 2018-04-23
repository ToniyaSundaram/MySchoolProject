package com.example.api

import com.example.flow.EditFlow.Editor
import com.example.flow.CreateFlow.Initiator
import com.example.state.SchoolState
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import org.slf4j.Logger
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.core.Response.Status.CREATED

val SERVICE_NAMES = listOf("Notary", "Network Map Service")

// This API is accessible from /api/example. All paths specified below are relative to it.
@Path("example")
class ExampleApi(private val rpcOps: CordaRPCOps) {
    private val myLegalName: CordaX500Name = rpcOps.nodeInfo().legalIdentities.first().name

    companion object {
        private val logger: Logger = loggerFor<ExampleApi>()
    }

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun getPeers(): Map<String, List<CordaX500Name>> {
        val nodeInfo = rpcOps.networkMapSnapshot()
        return mapOf("peers" to nodeInfo
                .map { it.legalIdentities.first().name }
                //filter out myself, notary and eventual network map started by driver
                .filter { it.organisation !in (SERVICE_NAMES + myLegalName.organisation) })
    }

    /**
     * Displays all IOU states that exist in the node's vault.
     */
    @GET
    @Path("ious")
    @Produces(MediaType.APPLICATION_JSON)
    fun getIOUs() = rpcOps.vaultQueryBy<SchoolState>().states

    /**
     * Initiates a flow to agree an IOU between two parties.
     *
     * Once the flow finishes it will have written the IOU to ledger. Both the lender and the borrower will be able to
     * see it when calling /api/example/ious on their respective nodes.
     *
     * This end-point takes a Party name parameter as part of the path. If the serving node can't find the other party
     * in its network map cache, it will return an HTTP bad request.
     *
     * The flow is invoked asynchronously. It returns a future when the flow's call() method returns.
     */
    @PUT
    @Path("create-student")
    fun createStudent(@QueryParam("name") name: String,@QueryParam("age") age:Int,@QueryParam("studentid") studentid:Int,
                  @QueryParam("partyName") deo: CordaX500Name?): Response {
        if (studentid <= 0 ) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'studentid' must be non-negative.\n").build()
        }
        if (deo == null) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'partyName' missing or has wrong format.\n").build()
        }
        val deoNode = rpcOps.wellKnownPartyFromX500Name(deo) ?:
                return Response.status(BAD_REQUEST).entity("Party named $deo cannot be found.\n").build()

        return try {
            val signedTx = rpcOps.startTrackedFlow(::Initiator, name,age,studentid, deoNode).returnValue.getOrThrow()
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()

        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build()
        }
    }

    @PUT
    @Path("edit-studentInfo")
    fun editStudent(@QueryParam("age")age:Int,@QueryParam("studentId") studentId:Int,@QueryParam("School") School:CordaX500Name?):Response {
        if(age <=0) {
            return Response.status(BAD_REQUEST).entity("Query parameter 'studentid' must be non-negative.\n").build()
        }
        val deoNode = rpcOps.wellKnownPartyFromX500Name(CordaX500Name("deo", "London", "GB")) ?:
        return Response.status(BAD_REQUEST).entity("deo node cannot be found.\n").build()

        val SchoolNode = rpcOps.wellKnownPartyFromX500Name(School!!) ?:
        return Response.status(BAD_REQUEST).entity("Party named $School cannot be found.\n").build()

        return  try {
            val signedTx = rpcOps.startTrackedFlow(::Editor, studentId, age,deoNode,SchoolNode).returnValue.getOrThrow();
            Response.status(CREATED).entity("Transaction id ${signedTx.id} committed to ledger.\n").build()
        } catch (ex: Throwable) {
            logger.error(ex.message, ex)
            Response.status(BAD_REQUEST).entity(ex.message!!).build();
        }
    }

}