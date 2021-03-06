/*
 * Copyright (C) 2010 Teleal GmbH, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.teleal.cling.model;

import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.Service;
import org.teleal.cling.model.resource.Resource;
import org.teleal.common.util.URIUtil;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Enforces path conventions for all locally offered resources (descriptors, icons, etc.)
 * <p>
 * Every descriptor, icon, event callback, or action message is send to a URL. This namespace
 * defines how the path of this URL will look like and it will build the path for a given
 * resource.
 * </p>
 * <p>
 * By default, the namespace is organized as follows:
 * </p>
 * <pre>{@code
 *http://host:port/dev/<udn>/desc.xml
 *http://host:port/dev/<udn>/svc/<svcIdNamespace>/<svcId>/desc.xml
 *http://host:port/dev/<udn>/svc/<svcIdNamespace>/<svcId>/action
 *http://host:port/dev/<udn>/svc/<svcIdNamespace>/<svcId>/event
 *http://host:port/dev/<ThisIsEitherRootUDN>/svc/<svcIdNamespace>/<svcId>/event/cb.xml
 *http://host:port/dev/<OrEvenAnEmbeddedDevicesUDN>/svc/<svcIdNamespace>/<svcId>/action
 *...
 * }</pre>
 * <p>
 * The namespace is also used to discover and create all {@link org.teleal.cling.model.resource.Resource}s
 * given a {@link org.teleal.cling.model.meta.Device}'s metadata. This procedure is typically
 * invoked once, when the device is added to the {@link org.teleal.cling.registry.Registry}.
 * </p>
 *
 * @author Christian Bauer
 */
public class Namespace {

    final private static Logger log = Logger.getLogger(Namespace.class.getName());

    public static final String DEVICE = "/dev";
    public static final String SERVICE = "/svc";
    public static final String CONTROL = "/action";
    public static final String EVENTS = "/event";
    public static final String DESCRIPTOR_FILE = "/desc.xml";
    public static final String CALLBACK_FILE = "/cb.xml";

    final protected URI basePath;

    public Namespace() {
        this.basePath = URI.create("");
    }

    public Namespace(String basePath) {
        this(URI.create(basePath));
    }

    public Namespace(URI basePath) {
        this.basePath = basePath;
    }

    public URI getBasePath() {
        return basePath;
    }

    public URI getPath(Device device) {
        if (device.getIdentity().getUdn() == null) {
            throw new IllegalStateException("Can't generate local URI prefix without UDN");
        }
        StringBuilder s = new StringBuilder();
        s.append(DEVICE).append("/");

        s.append(URIUtil.encodePathSegment(device.getIdentity().getUdn().getIdentifierString()));

        /*
        We no longer need the hierarchical URIs, in fact, they are impossible to parse
        with typical URI template support in various REST engines.
        if (device.isRoot()) {
            s.append(device.getIdentity().getUdn().getIdentifierString());
        } else {
            List<Device> devices = new ArrayList();
            Device temp = device;
            while (temp != null) {
                devices.add(temp);
                temp = temp.getParentDevice();
            }
            Collections.reverse(devices);
            for (Device d : devices) {
                if (d == device) continue;
                s.append(d.getIdentity().getUdn().getIdentifierString());
                s.append(EMBEDDED);
                s.append("/");
            }
            s.append(device.getIdentity().getUdn().getIdentifierString());
        }
        */
        return URI.create(getBasePath().toString() + s.toString());
    }

    public URI getPath(Service service) {
        if (service.getServiceId() == null) {
            throw new IllegalStateException("Can't generate local URI prefix without service ID");
        }
        StringBuilder s = new StringBuilder();
        s.append(SERVICE);
        s.append("/");
        s.append(service.getServiceId().getNamespace());
        s.append("/");
        s.append(service.getServiceId().getId());
        return URI.create(getPath(service.getDevice()).toString() + s.toString());
    }

    public URI getDescriptorPath(Device device) {
        return URI.create(getPath(device.getRoot()).toString() + DESCRIPTOR_FILE);
    }

    public URI getDescriptorPath(Service service) {
        return URI.create(getPath(service).toString() + DESCRIPTOR_FILE);
    }

    public URI getControlPath(Service service) {
        return URI.create(getPath(service).toString() + CONTROL);
    }

    public URI getEventSubscriptionPath(Service service) {
        return URI.create(getPath(service).toString() + EVENTS);
    }

    public URI getEventCallbackPath(Service service) {
        return URI.create(getEventSubscriptionPath(service).toString() + CALLBACK_FILE);
    }

    public URI prefixIfRelative(Device device, URI uri) {
        if (!uri.isAbsolute() && !uri.getPath().startsWith("/")) {
            return URI.create(getPath(device).toString() + "/" + uri.toString());
        }
        return uri;
    }

    public boolean isControlPath(URI uri) {
        return uri.toString().endsWith(Namespace.CONTROL);
    }

    public boolean isEventSubscriptionPath(URI uri) {
        return uri.toString().endsWith(Namespace.EVENTS);
    }

    public boolean isEventCallbackPath(URI uri) {
        return uri.toString().endsWith(Namespace.CALLBACK_FILE);
    }

    public Resource[] getResources(Device device) throws ValidationException {
        if (!device.isRoot()) return null;

        Set<Resource> resources = new HashSet();
        List<ValidationError> errors = new ArrayList();

        log.fine("Discovering local resources of device graph");
        Resource[] discoveredResources = device.discoverResources(this);
        for (Resource resource : discoveredResources) {
            log.finer("Discovered: " + resource);
            if (!resources.add(resource)) {
                log.finer("Local resource already exists, queueing validation error");
                errors.add(new ValidationError(
                        getClass(),
                        "resources",
                        "Local URI namespace conflict between resources of device: " + resource
                ));
            }
        }
        if (errors.size() > 0) {
            throw new ValidationException("Validation of device graph failed, call getErrors() on exception", errors);
        }
        return resources.toArray(new Resource[resources.size()]);
    }



}
