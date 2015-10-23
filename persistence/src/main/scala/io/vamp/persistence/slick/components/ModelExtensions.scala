package io.vamp.persistence.slick.components

import io.vamp.persistence.slick.extension.VampActiveSlick

trait ModelExtensions extends Schema
    with DeploymentExtensions
    with DeploymentClusterExtensions
    with DeploymentServiceExtensions
    with DeploymentServerExtensions
    with BlueprintReferenceExtensions
    with ClusterExtensions
    with DefaultBlueprintExtensions
    with GenericEscalationExtensions
    with DefaultFilterExtensions
    with DefaultRoutingExtensions
    with DefaultScaleExtensions
    with GenericSlaExtensions
    with EscalationReferenceExtensions
    with FilterReferenceExtensions
    with RoutingReferenceExtensions
    with ScaleReferenceExtensions
    with ServiceExtensions
    with SlaReferenceExtensions
    with DefaultBreedExtensions {
  this: VampActiveSlick ⇒
}
