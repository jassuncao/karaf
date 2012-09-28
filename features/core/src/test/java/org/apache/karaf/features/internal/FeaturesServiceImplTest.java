/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.features.internal;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.felix.utils.manifest.Clause;
import org.apache.karaf.features.Feature;
import org.apache.karaf.features.internal.BundleManager.BundleInstallerResult;
import org.easymock.EasyMock;
import org.osgi.framework.Bundle;

/**
 * Test cases for {@link FeaturesServiceImpl}
 */
public class FeaturesServiceImplTest extends TestCase {
    
    File dataFile;

    protected void setUp() throws IOException {
        dataFile = File.createTempFile("features", null, null);
    }

    public void testGetFeature() throws Exception {
        final Map<String, Map<String, Feature>> features = new HashMap<String, Map<String,Feature>>();
        Map<String, Feature> versions = new HashMap<String, Feature>();
        org.apache.karaf.features.internal.model.Feature feature = new org.apache.karaf.features.internal.model.Feature("transaction");
        versions.put("1.0.0", feature);
        features.put("transaction", versions);
        final FeaturesServiceImpl impl = new FeaturesServiceImpl(null, null) {
            protected Map<String,Map<String,Feature>> getFeatures() throws Exception {
                return features;
            };
        };
        assertNotNull(impl.getFeature("transaction", org.apache.karaf.features.internal.model.Feature.DEFAULT_VERSION));
        assertSame(feature, impl.getFeature("transaction", org.apache.karaf.features.internal.model.Feature.DEFAULT_VERSION));
    }
    
    public void testGetFeatureStripVersion() throws Exception {
        final Map<String, Map<String, Feature>> features = new HashMap<String, Map<String,Feature>>();
        Map<String, Feature> versions = new HashMap<String, Feature>();
        org.apache.karaf.features.internal.model.Feature feature = new org.apache.karaf.features.internal.model.Feature("transaction");
        versions.put("1.0.0", feature);
        features.put("transaction", versions);
        final FeaturesServiceImpl impl = new FeaturesServiceImpl(null, null) {
            protected Map<String,Map<String,Feature>> getFeatures() throws Exception {
                return features;
            };
        };
        assertNotNull(impl.getFeature("transaction", "  1.0.0  "));
        assertSame(feature, impl.getFeature("transaction", "  1.0.0   "));
    }
    
    public void testGetFeatureNotAvailable() throws Exception {
        final Map<String, Map<String, Feature>> features = new HashMap<String, Map<String,Feature>>();
        Map<String, Feature> versions = new HashMap<String, Feature>();
        versions.put("1.0.0", new org.apache.karaf.features.internal.model.Feature("transaction"));
        features.put("transaction", versions);
        final FeaturesServiceImpl impl = new FeaturesServiceImpl(null, null) {
            protected Map<String,Map<String,Feature>> getFeatures() throws Exception {
                return features;
            };
        };
        assertNull(impl.getFeature("activemq", org.apache.karaf.features.internal.model.Feature.DEFAULT_VERSION));
    }
    
    public void testGetFeatureHighestAvailable() throws Exception {
        final Map<String, Map<String, Feature>> features = new HashMap<String, Map<String,Feature>>();
        Map<String, Feature> versions = new HashMap<String, Feature>();
        versions.put("1.0.0", new org.apache.karaf.features.internal.model.Feature("transaction", "1.0.0"));
        versions.put("2.0.0", new org.apache.karaf.features.internal.model.Feature("transaction", "2.0.0"));
        features.put("transaction", versions);
        final FeaturesServiceImpl impl = new FeaturesServiceImpl(null, null) {
            protected Map<String,Map<String,Feature>> getFeatures() throws Exception {
                return features;
            };
        };
        assertNotNull(impl.getFeature("transaction", org.apache.karaf.features.internal.model.Feature.DEFAULT_VERSION));
        assertSame("2.0.0", impl.getFeature("transaction", org.apache.karaf.features.internal.model.Feature.DEFAULT_VERSION).getVersion());
    }

    public void testStartDoesNotFailWithOneInvalidUri()  {
        BundleManager bundleManager = EasyMock.createMock(BundleManager.class);
        expect(bundleManager.getDataFile(EasyMock.<String>anyObject())).andReturn(dataFile).anyTimes();
        expect(bundleManager.createAndRegisterEventAdminListener()).andReturn(null);
        replay(bundleManager);
        FeaturesServiceImpl service = new FeaturesServiceImpl(bundleManager, null);
        try {
            service.setUrls("mvn:inexistent/features/1.0/xml/features");
            service.start();
        } catch (Exception e) {
            fail(String.format("Service should not throw start-up exception but log the error instead: %s", e));
        }
    }

    /**
     * This test checks KARAF-388 which allows you to specify version of boot feature.
     */
    @SuppressWarnings("unchecked")
    public void testStartDoesNotFailWithNonExistentVersion()  {
        BundleManager bundleManager = EasyMock.createMock(BundleManager.class);
        expect(bundleManager.createAndRegisterEventAdminListener()).andReturn(null);
        bundleManager.refreshBundles(EasyMock.anyObject(InstallationState.class), EasyMock.anyObject(EnumSet.class));
        EasyMock.expectLastCall().anyTimes();

        final Map<String, Map<String, Feature>> features = new HashMap<String, Map<String,Feature>>();
        Map<String, Feature> versions = new HashMap<String, Feature>();
        versions.put("1.0.0", new org.apache.karaf.features.internal.model.Feature("transaction", "1.0.0"));
        versions.put("2.0.0", new org.apache.karaf.features.internal.model.Feature("transaction", "2.0.0"));
        features.put("transaction", versions);

        Map<String, Feature> versions2 = new HashMap<String, Feature>();
        versions2.put("1.0.0", new org.apache.karaf.features.internal.model.Feature("ssh", "1.0.0"));
        features.put("ssh", versions2);

        final FeaturesServiceImpl impl = new FeaturesServiceImpl(bundleManager, null) {
            protected Map<String,Map<String,Feature>> getFeatures() throws Exception {
                return features;
            };

            // override methods which refers to bundle context to avoid mocking everything
            @Override
            protected boolean loadState() {
                return true;
            }
            @Override
            protected void saveState() {
            }
        };
       
        BootFeaturesInstaller bootFeatures = new BootFeaturesInstaller(impl, "transaction;version=1.2,ssh;version=1.0.0");
        replay(bundleManager);
        try {
            Thread.currentThread().setContextClassLoader(new URLClassLoader(new URL[0]));
            impl.start();
            bootFeatures.installBootFeatures();
            assertFalse("Feature transaction 1.0.0 should not be installed", impl.isInstalled(impl.getFeature("transaction", "1.0.0")));
            assertFalse("Feature transaction 2.0.0 should not be installed", impl.isInstalled(impl.getFeature("transaction", "2.0.0")));
            assertTrue("Feature ssh should be installed", impl.isInstalled(impl.getFeature("ssh", "1.0.0")));
        } catch (Exception e) {
            fail(String.format("Service should not throw start-up exception but log the error instead: %s", e));
        }
        
    }
    
    public Bundle createDummyBundle(long id, String symbolicName) {
        Bundle bundle = EasyMock.createNiceMock(Bundle.class);
        expect(bundle.getBundleId()).andReturn(id).anyTimes();
        expect(bundle.getSymbolicName()).andReturn(symbolicName);
        expect(bundle.getHeaders()).andReturn(new Hashtable<String, String>());
        replay(bundle);
        return bundle;
    }

    /**
     * This test ensures that every feature get installed only once, even if it appears multiple times in the list
     * of transitive feature dependencies (KARAF-1600)
     */
    @SuppressWarnings("unchecked")
    public void testNoDuplicateFeaturesInstallation() throws Exception {
        final List<Feature> installed = new LinkedList<Feature>();
        BundleManager bundleManager = EasyMock.createMock(BundleManager.class);
        expect(bundleManager.installBundleIfNeeded(EasyMock.anyObject(String.class), EasyMock.anyInt(), EasyMock.anyObject(String.class)))
            .andReturn(new BundleInstallerResult(createDummyBundle(1l, ""), true)).anyTimes();
        bundleManager.refreshBundles(EasyMock.anyObject(InstallationState.class), EasyMock.anyObject(EnumSet.class));
        EasyMock.expectLastCall();
        final FeaturesServiceImpl impl = new FeaturesServiceImpl(bundleManager, null) {
            // override methods which refers to bundle context to avoid mocking everything
            @Override
            protected boolean loadState() {
                return true;
            }

            @Override
            protected void saveState() {

            }

            @Override
            protected void doInstallFeature(InstallationState state, Feature feature, boolean verbose) throws Exception {
                installed.add(feature);

                super.doInstallFeature(state, feature, verbose);
            }

        };
        replay(bundleManager);
        impl.addRepository(getClass().getResource("repo2.xml").toURI());
        impl.installFeature("all");

        // copying the features to a set to filter out the duplicates
        Set<Feature> noduplicates = new HashSet<Feature>();
        noduplicates.addAll(installed);

        assertEquals("Every feature should only have been installed once", installed.size(), noduplicates.size());
    }

    public void testGetOptionalImportsOnly() {
        BundleManager bundleManager = new BundleManager(null, null, 0l);

        List<Clause> result = bundleManager.getOptionalImports("org.apache.karaf,org.apache.karaf.optional;resolution:=optional");
        assertEquals("One optional import expected", 1, result.size());
        assertEquals("org.apache.karaf.optional", result.get(0).getName());

        result = bundleManager.getOptionalImports(null);
        assertNotNull(result);
        assertEquals("No optional imports expected", 0, result.size());
    }
}
