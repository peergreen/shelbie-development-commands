/**
 * Copyright 2013 Peergreen S.A.S. All rights reserved.
 * Proprietary and confidential.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.peergreen.shelbie.dev.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.ipojo.annotations.Component;
import org.apache.felix.ipojo.annotations.HandlerDeclaration;
import org.apache.felix.service.command.CommandSession;
import org.fusesource.jansi.Ansi;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Version;
import org.osgi.framework.namespace.PackageNamespace;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

@Component
@Command(name = "package-conflicts",
         scope = "dev",
         description = "Display conflicting packages")
@HandlerDeclaration("<sh:command xmlns:sh='org.ow2.shelbie'/>")
public class ConflictingPackagesAction implements Action {

    private final BundleContext bundleContext;

    public ConflictingPackagesAction(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public Object execute(final CommandSession session) throws Exception {

        Ansi buffer = Ansi.ansi();
        List<String> analyzed = new ArrayList<>();

        // Iterates on all the resolved or active bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if ((Bundle.ACTIVE == bundle.getState()) || (Bundle.RESOLVED == bundle.getState())) {
                BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

                // Ignore this wiring if not in use
                if (!bundleWiring.isInUse()) {
                    continue;
                }

                analyzeBundlePackages(buffer, bundleWiring, analyzed);

            }
        }

        System.out.print(buffer);
        return null;
    }

    private void analyzeBundlePackages(final Ansi buffer, final BundleWiring bundleWiring, final List<String> analyzed) {
        List<BundleCapability> capabilities = bundleWiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
        if (capabilities != null) {
            for (BundleCapability capability : capabilities) {
                Map<String, Object> attributes = capability.getAttributes();
                String value = (String) attributes.get(PackageNamespace.PACKAGE_NAMESPACE);

                List<BundleCapability> duplicatedPackages = findDuplicatedPackages(value, bundleWiring.getResource());
                if (!duplicatedPackages.isEmpty()) {

                    // Ignore already analyzed duplicated packages
                    if (analyzed.contains(value)) {
                        continue;
                    }
                    analyzed.add(value);

                    buffer.render("Package @|bold %s|@ has potential conflicts between: %n", value);
                    printPackageCapability(buffer, capability);
                    printImporterBundles(buffer, capability);
                    for (BundleCapability duplicated : duplicatedPackages) {
                        printPackageCapability(buffer, duplicated);
                        printImporterBundles(buffer, duplicated);
                    }
                }

            }
        }

    }

    private void printImporterBundles(final Ansi buffer, final BundleCapability duplicated) {
        BundleWiring wiring = duplicated.getResource().getWiring();
        List<BundleWire> providedWires = wiring.getProvidedWires(PackageNamespace.PACKAGE_NAMESPACE);
        if (providedWires != null) {
            for (BundleWire wire : providedWires) {
                if (wire.getCapability().equals(duplicated)) {
                    Bundle requirer = wire.getRequirer().getBundle();
                    buffer.render("    imported by %s/%s [%d]%n", requirer.getSymbolicName(), requirer.getVersion(), requirer.getBundleId());
                }
            }
        }
    }

    private void printPackageCapability(final Ansi buffer, final BundleCapability capability) {
        Bundle bundle = capability.getResource().getBundle();
        Version version = (Version) capability.getAttributes().get(PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE);

        buffer.render("  version <@|red %s|@> exported by @|bold %s|@/%s [%d]%n",
                      version,
                      bundle.getSymbolicName(),
                      bundle.getVersion(),
                      bundle.getBundleId());

        boolean attribute = false;
        String mandatory = (String) capability.getAttributes().get(PackageNamespace.CAPABILITY_MANDATORY_DIRECTIVE);
        if (mandatory != null) {
            buffer.render("    mandatory:=%s ", mandatory);
            attribute = true;
        }
        String effective = (String) capability.getAttributes().get(PackageNamespace.CAPABILITY_EFFECTIVE_DIRECTIVE);
        if (effective != null) {
            buffer.render("    effective:=%s ", effective);
            attribute = true;
        }
        String exclude = (String) capability.getAttributes().get(PackageNamespace.CAPABILITY_EXCLUDE_DIRECTIVE);
        if (exclude != null) {
            buffer.render("    exclude:=%s ", exclude);
            attribute = true;
        }
        String include = (String) capability.getAttributes().get(PackageNamespace.CAPABILITY_INCLUDE_DIRECTIVE);
        if (include != null) {
            buffer.render("    include:=%s ", include);
            attribute = true;
        }
        String uses = (String) capability.getAttributes().get(PackageNamespace.CAPABILITY_USES_DIRECTIVE);
        if (uses != null) {
            buffer.render("    uses:=%s ", uses);
            attribute = true;
        }

        if (attribute) {
            buffer.newline();
        }

    }

    private List<BundleCapability> findDuplicatedPackages(final String name, final BundleRevision resource) {
        List<BundleCapability> duplicates = new ArrayList<>();

        // Iterates on all the resolved or active bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if ((Bundle.ACTIVE == bundle.getState()) || (Bundle.RESOLVED == bundle.getState())) {
                BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

                // Ignore this wiring if not in use
                if (!bundleWiring.isInUse()) {
                    continue;
                }

                // Ignore the source bundle
                if (bundleWiring.getResource().equals(resource)) {
                    continue;
                }

                List<BundleCapability> capabilities = bundleWiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
                if (capabilities != null) {
                    for (BundleCapability capability : capabilities) {
                        Map<String, Object> attributes = capability.getAttributes();
                        String value = (String) attributes.get(PackageNamespace.PACKAGE_NAMESPACE);

                        if (name.equals(value)) {
                            // Got a duplicate
                            duplicates.add(capability);
                        }
                    }
                }
            }
        }

        return duplicates;
    }
}