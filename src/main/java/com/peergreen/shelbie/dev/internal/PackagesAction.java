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

import java.util.List;
import java.util.Map;

import org.apache.felix.gogo.commands.Action;
import org.apache.felix.gogo.commands.Argument;
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
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;

@Component
@Command(name = "packages",
         scope = "dev",
         description = "Display packages requirers")
@HandlerDeclaration("<sh:command xmlns:sh='org.ow2.shelbie'/>")
public class PackagesAction implements Action {

    @Argument(name = "package",
              description = "Package name to display",
              required = true)
    private String packageName;

    private final BundleContext bundleContext;

    public PackagesAction(final BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public Object execute(final CommandSession session) throws Exception {

        Ansi buffer = Ansi.ansi();

        // Iterates on all the resolved or active bundles
        for (Bundle bundle : bundleContext.getBundles()) {
            if ((Bundle.ACTIVE == bundle.getState()) || (Bundle.RESOLVED == bundle.getState())) {
                BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);

                // Ignore this wiring if not in use
                if (!bundleWiring.isInUse()) {
                    continue;
                }

                List<BundleCapability> capabilities = bundleWiring.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);
                if (capabilities != null) {
                    for (BundleCapability capability : capabilities) {
                        Map<String, Object> attributes = capability.getAttributes();
                        String value = (String) attributes.get(PackageNamespace.PACKAGE_NAMESPACE);
                        if (packageName.equals(value)) {
                            // Got an exported package with the expected name
                            Version version = (Version) attributes.get(PackageNamespace.CAPABILITY_VERSION_ATTRIBUTE);

                            printPackageHeader(buffer, packageName, version, bundle);
                            printImporterBundles(buffer, capability, bundleWiring);
                        }
                    }
                }

            }
        }

        System.out.print(buffer);
        return null;
    }

    private void printImporterBundles(final Ansi buffer, final BundleCapability capability, final BundleWiring bundleWiring) {
        List<BundleWire> wires = bundleWiring.getProvidedWires(PackageNamespace.PACKAGE_NAMESPACE);
        if (wires != null) {
            for (BundleWire wire : wires) {
                if (capability.equals(wire.getCapability())) {
                    // Got a wire with the expected name
                    Bundle requirer = wire.getRequirer().getBundle();
                    buffer.render("  @|faint imported by|@ %s/%s [%d]%n", requirer.getSymbolicName(), requirer.getVersion(), requirer.getBundleId());
                }
            }
        }
    }

    private void printPackageHeader(final Ansi buffer, final String packageName, final Version version, final Bundle bundle) {
        buffer.render("@|bold %s|@ %s from %s/%s [%d]%n", packageName, version, bundle.getSymbolicName(), bundle.getVersion(), bundle.getBundleId());
    }

}