/*
 * Copyright (c) 2015 the original author or authors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Etienne Studer & Donát Csikós (Gradle Inc.) - initial API and implementation and initial documentation
 */

package com.gradleware.tooling.eclipse.ui.taskview.adapter;

import com.gradleware.tooling.eclipse.core.GradlePluginsRuntimeException;
import com.gradleware.tooling.eclipse.ui.taskview.ProjectTaskNode;
import com.gradleware.tooling.eclipse.ui.taskview.TaskNode;
import com.gradleware.tooling.eclipse.ui.taskview.TaskSelectorNode;
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.ui.views.properties.IPropertySource;

/**
 * Adapts {@link TaskNode} instances to {@link IPropertySource} instances.
 * <p/>
 * By adapting to {@link IPropertySource} instances, the Eclipse Properties View can automatically
 * display the properties of a task node upon selection.
 * <p/>
 * The adapter is registered with the Eclipse platform via the {@code org.eclipse.core.runtime.adapters} extension point.
 * <p/>
 * Description on the adapters is available at
 * <a href= "http://www.programcreek.com/2012/01/decipher-eclipse-architecture-iadaptable-part-1-brief-introduction">
 *     http://www.programcreek.com/2012/01/decipher-eclipse-architecture-iadaptable-part-1-brief-introduction</a>.
 */
@SuppressWarnings("rawtypes")
public final class TaskNodeAdapterFactory implements IAdapterFactory {

    @Override
    public Object getAdapter(Object adaptableObject, Class adapterType) {
        if (adapterType == IPropertySource.class && adaptableObject instanceof TaskNode) {
            return createTaskNodeAdapter((TaskNode) adaptableObject);
        } else {
            return null;
        }
    }

    private IPropertySource createTaskNodeAdapter(TaskNode adaptableObject) {
        if (adaptableObject instanceof ProjectTaskNode) {
            return new ProjectTaskNodeAdapter((ProjectTaskNode) adaptableObject);
        } else if (adaptableObject instanceof TaskSelectorNode) {
            return new TaskSelectorNodeAdapter((TaskSelectorNode) adaptableObject);
        } else {
            throw new GradlePluginsRuntimeException("Unknown task node type: " + adaptableObject.getClass());
        }
    }

    @Override
    public Class[] getAdapterList() {
        return new Class[]{IPropertySource.class};
    }

}