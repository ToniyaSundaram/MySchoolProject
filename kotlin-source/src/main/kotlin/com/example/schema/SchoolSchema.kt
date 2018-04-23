package com.example.schema

import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Table

/**
 * The family of schemas for IOUState.
 */
object SchoolSchema

/**
 * An IOUState schema.
 */
object SchoolSchemaV1 : MappedSchema(
        schemaFamily = SchoolSchema.javaClass,
        version = 1,
        mappedTypes = listOf(PersistentSchool::class.java)) {
    @Entity
    @Table(name = "school_states")
    class PersistentSchool(
            @Column(name = "school")
            var school: String,

            @Column(name = "deo")
            var deo: String,

            @Column(name = "name")
            var name: String,

            @Column(name = "age")
            var age: Int,

            @Column(name = "id")
            var id: Int,

            @Column(name = "linear_id")
            var linearId: UUID
    ) : PersistentState() {
        // Default constructor required by hibernate.
        constructor(): this("", "","",0, 0, UUID.randomUUID())
    }
}