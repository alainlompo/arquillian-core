/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.test.impl.client.deployment;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jboss.arquillian.config.descriptor.impl.ContainerDefImpl;
import org.jboss.arquillian.container.impl.LocalContainerRegistry;
import org.jboss.arquillian.container.spi.Container;
import org.jboss.arquillian.container.spi.ContainerRegistry;
import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.deployment.DeploymentScenario;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.container.test.api.OverProtocol;
import org.jboss.arquillian.container.test.api.TargetsContainer;
import org.jboss.arquillian.container.test.impl.client.deployment.event.GenerateDeployment;
import org.jboss.arquillian.container.test.impl.domain.ProtocolDefinition;
import org.jboss.arquillian.container.test.impl.domain.ProtocolRegistry;
import org.jboss.arquillian.container.test.spi.client.deployment.ApplicationArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveAppender;
import org.jboss.arquillian.container.test.spi.client.deployment.AuxiliaryArchiveProcessor;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentPackager;
import org.jboss.arquillian.container.test.spi.client.deployment.DeploymentScenarioGenerator;
import org.jboss.arquillian.container.test.spi.client.protocol.Protocol;
import org.jboss.arquillian.container.test.test.AbstractContainerTestTestBase;
import org.jboss.arquillian.core.api.Injector;
import org.jboss.arquillian.core.api.Instance;
import org.jboss.arquillian.core.api.annotation.ApplicationScoped;
import org.jboss.arquillian.core.api.annotation.Inject;
import org.jboss.arquillian.core.spi.ServiceLoader;
import org.jboss.arquillian.test.spi.TestClass;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * DeploymentGeneratorTestCase
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
@RunWith(MockitoJUnitRunner.class)
public class DeploymentGeneratorTestCase extends AbstractContainerTestTestBase
{
   public static final String PROTOCOL_NAME_1 = "TEST_DEFAULT_1";
   public static final String PROTOCOL_NAME_2 = "TEST_DEFAULT_2";
   public static final String CONTAINER_NAME_1 = "CONTAINER_NAME_1";
   
   
   @Override
   protected void addExtensions(List<Class<?>> extensions)
   {
      extensions.add(DeploymentGenerator.class);
   }
   
   @Inject 
   private Instance<Injector> injectorInst;
   
   @Mock
   private ServiceLoader serviceLoader;

   private ContainerRegistry containerRegistry;
   
   private ProtocolRegistry protocolRegistry;
   
   @Mock
   @SuppressWarnings("rawtypes")
   private DeployableContainer deployableContainer;

   @Mock
   private DeploymentPackager packager;

   
   @Before
   public void prepare() 
   {
      Injector injector = injectorInst.get();
      
      when(serviceLoader.onlyOne(DeploymentScenarioGenerator.class, AnnotationDeploymentScenarioGenerator.class))
               .thenReturn(new AnnotationDeploymentScenarioGenerator());
      when(serviceLoader.onlyOne(eq(DeployableContainer.class))).thenReturn(deployableContainer);
      when(deployableContainer.getDefaultProtocol()).thenReturn(new ProtocolDescription(PROTOCOL_NAME_1));
      
      when(serviceLoader.all(eq(AuxiliaryArchiveAppender.class)))
         .thenReturn(create(AuxiliaryArchiveAppender.class, injector.inject(new TestAuxiliaryArchiveAppender())));
      when(serviceLoader.all(eq(AuxiliaryArchiveProcessor.class)))
         .thenReturn(create(AuxiliaryArchiveProcessor.class, injector.inject(new TestAuxiliaryArchiveProcessor())));
      when(serviceLoader.all(eq(ApplicationArchiveProcessor.class)))
         .thenReturn(create(ApplicationArchiveProcessor.class, injector.inject(new TestApplicationArchiveAppender())));

      containerRegistry = new LocalContainerRegistry(injector);
      protocolRegistry = new ProtocolRegistry();
      
      bind(ApplicationScoped.class, ServiceLoader.class, serviceLoader);
      bind(ApplicationScoped.class, ContainerRegistry.class, containerRegistry);
      bind(ApplicationScoped.class, ProtocolRegistry.class, protocolRegistry);
      bind(ApplicationScoped.class, CallMap.class, new CallMap());
   }

   @Test
   public void shouldUseDefaultDefinedProtocolIfFound()
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      addProtocol(PROTOCOL_NAME_1, true);
      
      fire(createEvent(DeploymentWithDefaults.class));
      
      verify(deployableContainer, times(0)).getDefaultProtocol();
   }
   
   @Test
   public void shouldUseContainerDefaultProtocolIfNonDefaultDefined() 
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      addProtocol(PROTOCOL_NAME_1, false);
      addProtocol(PROTOCOL_NAME_2, false);
      
      fire(createEvent(DeploymentWithDefaults.class));
      
      verify(deployableContainer, times(1)).getDefaultProtocol();
      verifyScenario("_DEFAULT_");
   }

   @Test
   public void shouldCallPackagingSPIsOnTestableArchive() throws Exception
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      addProtocol(PROTOCOL_NAME_1, true);
      
      fire(createEvent(DeploymentWithDefaults.class));
      
      CallMap spi = getManager().resolve(CallMap.class);
      Assert.assertTrue(spi.wasCalled(ApplicationArchiveProcessor.class));
      Assert.assertTrue(spi.wasCalled(AuxiliaryArchiveAppender.class));
      Assert.assertTrue(spi.wasCalled(AuxiliaryArchiveProcessor.class));
   
      verifyScenario("_DEFAULT_");
   }

   @Test
   public void shouldNotCallPackagingSPIsOnNonTestableArchive() throws Exception
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      addProtocol(PROTOCOL_NAME_1, true);
      
      fire(createEvent(DeploymentNonTestableWithDefaults.class));
      
      CallMap spi = getManager().resolve(CallMap.class);
      Assert.assertFalse(spi.wasCalled(ApplicationArchiveProcessor.class));
      Assert.assertFalse(spi.wasCalled(AuxiliaryArchiveAppender.class));
      Assert.assertFalse(spi.wasCalled(AuxiliaryArchiveProcessor.class));

      verifyScenario("_DEFAULT_");
   }

   @Test
   public void shouldAllowNonManagedDeploymentOnCustomContainer() throws Exception
   {
      addContainer(CONTAINER_NAME_1).getContainerConfiguration().setMode("custom");
      fire(createEvent(DeploymentNonManagedWithCustomContainerReference.class));
      
      verifyScenario("DeploymentNonManagedWithCustomContainerReference");
   }

   @Test(expected = ValidationException.class)
   public void shouldThrowExceptionOnMissingContainerReference() throws Exception
   {
      try
      {
         fire(createEvent(DeploymentWithContainerReference.class));
      }
      catch (Exception e)
      {
         Assert.assertTrue("Validate correct error message", e.getMessage().contains("Please include at least 1 Deployable Container on your Classpath"));
         throw e;
      }
   }
   
   @Test(expected = ValidationException.class)
   public void shouldThrowExceptionOnWrongContainerReference() throws Exception
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      try
      {
         fire(createEvent(DeploymentWithContainerReference.class));
      }
      catch (Exception e)
      {
         //e.printStackTrace();
         Assert.assertTrue("Validate correct error message", e.getMessage().contains("does not match any found/configured Containers"));
         throw e;
      }
   }

   @Test(expected = ValidationException.class)
   public void shouldThrowExceptionOnMissingProtocolReference() throws Exception
   {
      addContainer("test-contianer").getContainerConfiguration().setMode("suite");
      try
      {
         fire(createEvent(DeploymentWithProtocolReference.class));
      }
      catch (Exception e)
      {
         Assert.assertTrue("Validate correct error message", e.getMessage().contains("not maching any defined Protocol"));
         throw e;
      }
   }

   @Test(expected = ValidationException.class)
   public void shouldThrowExceptionOnManagedDeploymentOnCustomContainer() throws Exception
   {
      addContainer(CONTAINER_NAME_1).getContainerConfiguration().setMode("custom");
      try
      {
         fire(createEvent(DeploymentManagedWithCustomContainerReference.class));
      }
      catch (Exception e)
      {
         Assert.assertTrue("Validate correct error message", e.getMessage().contains("This container is set to mode custom "));
         throw e;
      }
   }

   private void verifyScenario(String... names)
   {
      DeploymentScenario scenario = getManager().resolve(DeploymentScenario.class);
      Assert.assertEquals(names.length, scenario.deployments().size());
      
      for(int i = 0; i < names.length; i++)
      {
         scenario.deployments().get(i).getDescription().getName().equals(names[i]);
      }
   }

   private Container addContainer(String name)
   {
      return containerRegistry.create(
            new ContainerDefImpl("arquillian.xml")
               .container(name),
            serviceLoader);
   }
   
   private ProtocolDefinition addProtocol(String name, boolean shouldBeDefault)
   {
      Protocol<?> protocol = mock(Protocol.class);
      when(protocol.getPackager()).thenReturn(packager);
      when(protocol.getDescription()).thenReturn(new ProtocolDescription(name));

      Map<String, String> config = Collections.emptyMap();
      return protocolRegistry.addProtocol(new ProtocolDefinition(protocol, config, shouldBeDefault))
         .getProtocol(new ProtocolDescription(name));
   }

   private <T> Collection<T> create(Class<T> type, T... instances) 
   {
      List<T> list = new ArrayList<T>();
      for(T instance : instances)
      {
         list.add(instance);
      }
      return list;
   }

   private static class DeploymentWithDefaults
   {
      @SuppressWarnings("unused")
      @Deployment
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }

   private static class DeploymentNonTestableWithDefaults
   {
      @SuppressWarnings("unused")
      @Deployment(testable = false)
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }

   private static class DeploymentWithContainerReference
   {
      @SuppressWarnings("unused")
      @Deployment @TargetsContainer("DOES_NOT_EXIST")
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }

   private static class DeploymentWithProtocolReference
   {
      @SuppressWarnings("unused")
      @Deployment @OverProtocol("DOES_NOT_EXIST")
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }
   
   private static class DeploymentManagedWithCustomContainerReference
   {
      @SuppressWarnings("unused")
      @Deployment(managed = true, testable = false)
      @TargetsContainer(CONTAINER_NAME_1)
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }

   private static class DeploymentNonManagedWithCustomContainerReference
   {
      @SuppressWarnings("unused")
      @Deployment(name = "DeploymentNonManagedWithCustomContainerReference", managed = false, testable = false)
      @TargetsContainer(CONTAINER_NAME_1)
      public static JavaArchive deploy() 
      {
         return ShrinkWrap.create(JavaArchive.class);
      }
   }

   private GenerateDeployment createEvent(Class<?> testClass) 
   {
      return new GenerateDeployment(new TestClass(testClass));
   }
   
   private static class CallMap 
   {
      private Set<Class<?>> calls = new HashSet<Class<?>>();
      
      public void add(Class<?> called) 
      {
         calls.add(called);
      }
      
      public boolean wasCalled(Class<?> called)
      {
         return calls.contains(called);
      }
   }
   
   private static class TestMaker 
   {
      @Inject
      private Instance<CallMap> callmap;
      
      protected void called() 
      {
         callmap.get().add(super.getClass().getInterfaces()[0]);
      }
   }

   private static class TestAuxiliaryArchiveAppender extends TestMaker implements AuxiliaryArchiveAppender 
   {
      @Override
      public Archive<?> createAuxiliaryArchive()
      {
         called();
         return ShrinkWrap.create(JavaArchive.class, this.getClass().getSimpleName() + ".jar");
      }
   }

   private static class TestAuxiliaryArchiveProcessor extends TestMaker implements AuxiliaryArchiveProcessor 
   {
      @Override
      public void process(Archive<?> auxiliaryArchive)
      {
         called();
      }
   }
   
   private static class TestApplicationArchiveAppender extends TestMaker implements ApplicationArchiveProcessor 
   {
      @Override
      public void process(Archive<?> applicationArchive, TestClass testClass)
      {
         called();
      }
   }
}