package com.ibm.wala.shrikeBT;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.ibm.wala.shrikeCT.BootstrapMethodsReader.BootstrapMethod;

public class InvokeDynamicInstruction extends Instruction implements IInvokeInstruction {
  protected BootstrapMethod bootstrap;
  protected String methodName;
  protected String methodType;
  
  public InvokeDynamicInstruction(short opcode, BootstrapMethod bootstrap, String methodName, String methodType) {
    super(opcode);
    this.bootstrap = bootstrap;
    this.methodName = methodName;
    this.methodType = methodType;
  }

  @Override
  public boolean isPEI() {
    return true;
  }

  @Override
  public IDispatch getInvocationCode() {
    int invokeType = getBootstrap().invokeType();
    switch (invokeType) {
    case 5: return Dispatch.VIRTUAL;
    case 6: return Dispatch.STATIC;
    case 7: return Dispatch.SPECIAL;
    case 9: return Dispatch.INTERFACE;
    default:
      throw new Error("unexpected dynamic invoke type " + invokeType);
    }
  }

  @Override
  final public int getPoppedCount() {
    return (getInvocationCode().equals(Dispatch.STATIC) ? 0 : 1) + Util.getParamsCount(getMethodSignature());
  }

  @Override
  final public String getPushedType(String[] types) {
    String t = Util.getReturnType(getMethodSignature());
    if (t.equals(Constants.TYPE_void)) {
      return null;
    } else {
      return t;
    }
  }

  @Override
  final public byte getPushedWordSize() {
    String t = getMethodSignature();
    int index = t.lastIndexOf(')');
    return Util.getWordSize(t, index + 1);
  }
  
  public BootstrapMethod getBootstrap() {
    return bootstrap;
  }
  
  @Override
  public String getMethodSignature() {
     return methodType;
  }

  @Override
  public String getMethodName() {
    return methodName;
  }

  @Override
  public String getClassType() {
    return getBootstrap().methodClass();
  }

  @Override
  public void visit(Visitor v) {
    v.visitInvoke(this);
  }

  @Override
  public String toString() {
    return "InvokeDynamic [" + getBootstrap() + "] " + getMethodName() + getMethodSignature();
  }

  final static class Lazy extends InvokeDynamicInstruction {
    final private ConstantPoolReader cp;

    final private int index;

    Lazy(short opcode, ConstantPoolReader cp, int index) {
      super(opcode, null, null, null);
      this.index = index;
      this.cp = cp;
    }

    int getCPIndex() {
      return index;
    }

    @Override
    public BootstrapMethod getBootstrap() {
      if (bootstrap == null) {
        bootstrap = cp.getConstantPoolDynamicBootstrap(index);
      }
      return bootstrap;
    }
    
    @Override
    public String getMethodName() {
      if (methodName == null) {
        methodName = cp.getConstantPoolDynamicName(index);
      }
      return methodName;
    }

    @Override
    public String getMethodSignature() {
      if (methodType == null) {
        methodType = cp.getConstantPoolDynamicType(index);
      }
      return methodType;
    }
  }

  public CallSite bootstrap(Class cl) throws ClassNotFoundException, NoSuchMethodException, SecurityException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    ClassLoader bootstrapCL = cl.getClassLoader();

    Class<?> bootstrapClass = Class.forName(getBootstrap().methodClass().replace('/', '.'), false, bootstrapCL);
    MethodType bt = MethodType.fromMethodDescriptorString(bootstrap.methodType(), bootstrapCL);
    Method bootstrap = bootstrapClass.getMethod(this.bootstrap.methodName(), bt.parameterList().toArray(new Class[ bt.parameterCount() ]));
    Object[] args = new Object[ bt.parameterCount() ];
    args[0] = MethodHandles.lookup().in(cl);
    args[1] = getMethodName();
    args[2] = MethodType.fromMethodDescriptorString(getMethodSignature(), cl.getClassLoader());
    for(int i = 3; i < bt.parameterCount(); i++) {
      args[i] = getBootstrap().callArgument(i-3);
    }
    
    return (CallSite) bootstrap.invoke(null, args);
  }
  
  static InvokeDynamicInstruction make(ConstantPoolReader cp, int index, int mode) {
    if (mode != OP_invokedynamic) {
      throw new IllegalArgumentException("Unknown mode: " + mode);
    }
    return new Lazy((short) mode, cp, index);
  }

}
