/**
 * This file is part of the openSCADA project
 * 
 * Copyright (C) 2013 Jens Reimann (ctron@dentrassi.de)
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openscada.da.opc.configuration.tests;

import junit.framework.TestCase;

import junit.textui.TestRunner;

import org.openscada.da.opc.configuration.ConfigurationFactory;
import org.openscada.da.opc.configuration.ConfigurationType;

/**
 * <!-- begin-user-doc -->
 * A test case for the model object '<em><b>Type</b></em>'.
 * <!-- end-user-doc -->
 * @generated
 */
public class ConfigurationTypeTest extends TestCase
{

    /**
     * The fixture for this Type test case.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ConfigurationType fixture = null;

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public static void main ( String[] args )
    {
        TestRunner.run ( ConfigurationTypeTest.class );
    }

    /**
     * Constructs a new Type test case with the given name.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    public ConfigurationTypeTest ( String name )
    {
        super ( name );
    }

    /**
     * Sets the fixture for this Type test case.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected void setFixture ( ConfigurationType fixture )
    {
        this.fixture = fixture;
    }

    /**
     * Returns the fixture for this Type test case.
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @generated
     */
    protected ConfigurationType getFixture ()
    {
        return fixture;
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @see junit.framework.TestCase#setUp()
     * @generated
     */
    @Override
    protected void setUp () throws Exception
    {
        setFixture ( ConfigurationFactory.eINSTANCE.createConfigurationType () );
    }

    /**
     * <!-- begin-user-doc -->
     * <!-- end-user-doc -->
     * @see junit.framework.TestCase#tearDown()
     * @generated
     */
    @Override
    protected void tearDown () throws Exception
    {
        setFixture ( null );
    }

} //ConfigurationTypeTest
