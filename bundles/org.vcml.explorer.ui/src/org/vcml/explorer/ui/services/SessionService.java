/******************************************************************************
 *                                                                            *
 * Copyright 2018 Jan Henrik Weinstock                                        *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *     http://www.apache.org/licenses/LICENSE-2.0                             *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 *                                                                            *
 ******************************************************************************/

package org.vcml.explorer.ui.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.e4.ui.workbench.UIEvents;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.services.IDisposable;

import org.vcml.session.Module;
import org.vcml.session.Session;
import org.vcml.session.SessionException;

public class SessionService implements ISessionService, IDisposable {

    private List<Session> sessions = new ArrayList<Session>();

    private Session current = null;

    private ListenerList<IPropertyChangeListener> listeners = new ListenerList<IPropertyChangeListener>(
            ListenerList.IDENTITY);

    @Inject
    IEventBroker eventBroker;

    private void updateSession(String property, Session session) {
        if (eventBroker != null) {
            eventBroker.post(ISessionService.SESSION_TOPIC, session);
            eventBroker.post(UIEvents.REQUEST_ENABLEMENT_UPDATE_TOPIC, UIEvents.ALL_ELEMENT_ID);
        }

        if (!listeners.isEmpty()) {
            PropertyChangeEvent event = new PropertyChangeEvent(this, property, null, session);
            Object[] array = listeners.getListeners();
            for (int i = 0; i < array.length; i++)
                ((IPropertyChangeListener) array[i]).propertyChange(event);
        }
    }

    private void addSession(Session session) {
        if (session == null || sessions.contains(session))
            return;

        sessions.add(session);
        updateSession(PROP_ADDED, session);
    }

    private void removeSession(Session session) {
        if (session == null || !sessions.contains(session))
            return;

        sessions.remove(session);
        if (session == current)
            current = null;
        updateSession(PROP_REMOVED, session);
    }

    public SessionService() {
        System.out.println("session service created");
        refreshSessions();
    }

    @Override
    public Collection<Session> getSessions() {
        return Collections.unmodifiableCollection(sessions);
    }

    @Override
    public Session currentSession() {
        return current;
    }

    @Override
    public void refreshSession(Session session) {
        try {
            if (session == null || !session.isConnected() || session.isRunning())
                return;

            session.refresh();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void connectSession(Session session) {
        try {
            if (session.isConnected())
                return;
            session.connect();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void disconnectSession(Session session) {
        try {
            if (session == null)
                return;
            if (session.isRunning())
                stopSimulation(session);
            session.disconnect();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void startSimulation(Session session) {
        try {
            if (session == null || session.isRunning())
                return;
            if (!session.isConnected())
                connectSession(session);
            session.continueSimulation();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void stopSimulation(Session session) {
        try {
            if (session == null || !session.isRunning() || !session.isConnected())
                return;
            session.stopSimulation();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void stepSimulation(Session session) {
        try {
            if (session == null || session.isRunning())
                return;
            if (!session.isConnected())
                connectSession(session);
            session.stepSimulation();
            updateSession(PROP_UPDATED, session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public void quitSimulation(Session session) {
        try {
            if (session == null)
                return;
            if (!session.isConnected())
                connectSession(session);
            if (session.isRunning())
                stopSimulation(session);
            session.quitSimulation();
            session.disconnect();
            removeSession(session);
        } catch (SessionException e) {
            reportSessionError(session, e);
        }
    }

    @Override
    public Module findModule(Session session, String name) {
        try {
            if (session == null)
                return null;
            if (!session.isConnected())
                connectSession(session);
            return session.findObject(name);
        } catch (SessionException e) {
            reportSessionError(session, e);
            return null;
        }
    }

    @Override
    public void reportSessionError(Session session, SessionException e) {
        if (session.isConnected())
            disconnectSession(session);
        removeSession(session);
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if (cause != null)
            message += ": " + cause.getMessage();
        System.err.println(message);
        MessageDialog.openError(Display.getDefault().getActiveShell(), "Session Error", message);
    }

    @Override
    public void addSessionChangeListener(IPropertyChangeListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeSessionChangeListener(IPropertyChangeListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void dispose() {
        sessions.clear();
        listeners.clear();
    }

    @Override
    public void refreshSessions() {
        List<Session> available = Session.getAvailableSessions();
        for (Session session : available)
            addSession(session);
    }

    @Override
    public void addRemoteSession(String URI, boolean connect) {
        try {
            Session session = new Session(URI);
            addSession(session);
            if (connect)
                connectSession(session);
        } catch (SessionException e) {
            MessageDialog.openError(null, "Session management", e.getMessage());
        }
    }

    @Inject
    public void selectionChanged(@Optional @Named(IServiceConstants.ACTIVE_SELECTION) Session session) {
        if (session == null)
            return;

        current = session;
        updateSession(PROP_SELECT, current);
    }

}
