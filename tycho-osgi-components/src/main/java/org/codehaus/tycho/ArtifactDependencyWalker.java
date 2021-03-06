package org.codehaus.tycho;

import java.io.File;

import org.codehaus.tycho.model.Feature;
import org.codehaus.tycho.model.ProductConfiguration;
import org.codehaus.tycho.model.UpdateSite;

public interface ArtifactDependencyWalker
{
    /**
     * Walks the visitor through artifact dependencies
     */
    void walk( ArtifactDependencyVisitor visitor );

    /**
     * Walks dependencies of specified feature. Visitor is able to manipulate content
     * of the provided feature via PluginRef and FeatureRef instances provided as
     * via callback method parameters.
     */
    void traverseFeature( File location, Feature feature, ArtifactDependencyVisitor visitor );

    void traverseUpdateSite( UpdateSite site, ArtifactDependencyVisitor visitor );

    void traverseProduct( ProductConfiguration productConfiguration, ArtifactDependencyVisitor visitor );

}
