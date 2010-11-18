/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doclava;

import com.google.clearsilver.jsilver.data.Data;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Ordering;
import com.google.doclava.apicheck.AbstractMethodInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A method or constructor.
 */
public final class MethodInfo extends MemberInfo implements AbstractMethodInfo, Cloneable {
  public static final Ordering<MethodInfo> ORDER_BY_NAME_AND_SIGNATURE
      = new Ordering<MethodInfo>() {
    public int compare(MethodInfo a, MethodInfo b) {
      int result = a.name().compareTo(b.name());
      if (result != 0) {
        return result;
      }
      return a.signature().compareTo(b.signature());
    }
  };

  private class InlineTags implements InheritedTags {
    public List<TagInfo> tags() {
      return comment().tags();
    }

    public InheritedTags inherited() {
      MethodInfo m = findOverriddenMethod(name(), signature());
      if (m != null) {
        return m.inlineTags();
      } else {
        return null;
      }
    }
  }

  private static void addInterfaces(List<ClassInfo> ifaces, List<ClassInfo> queue) {
    for (ClassInfo i : ifaces) {
      queue.add(i);
    }
    for (ClassInfo i : ifaces) {
      addInterfaces(i.realInterfaces(), queue);
    }
  }

  // first looks for a superclass, and then does a breadth first search to
  // find the least far away match
  public MethodInfo findOverriddenMethod(String name, String signature) {
    if (mReturnType == null) {
      // ctor
      return null;
    }
    if (mOverriddenMethod != null) {
      return mOverriddenMethod;
    }

    // TODO: look at superclasses as well
    List<ClassInfo> queue = new ArrayList<ClassInfo>();
    addInterfaces(containingClass().realInterfaces(), queue);
    for (ClassInfo ifc : queue) {
      for (MethodInfo methodInfo : ifc.allSelfMethods()) {
        if (methodInfo.name().equals(name)
            && methodInfo.signature().equals(signature)
            && methodInfo.inlineTags().tags() != null
            && !methodInfo.inlineTags().tags().isEmpty()) {
          return methodInfo;
        }
      }
    }
    return null;
  }

  public MethodInfo findRealOverriddenMethod(MethodInfo other, Set<ClassInfo> notStrippable) {
    String name = other.name();
    String signature = other.signature();

    if (mReturnType == null) {
      // ctor
      return null;
    }
    if (mOverriddenMethod != null) {
      return mOverriddenMethod;
    }

    ArrayList<ClassInfo> queue = new ArrayList<ClassInfo>();
    if (containingClass().realSuperclass() != null
        && containingClass().realSuperclass().isAbstract()) {
      queue.add(containingClass());
    }
    addInterfaces(containingClass().realInterfaces(), queue);
    for (ClassInfo iface : queue) {
      for (MethodInfo me : iface.getMethods()) {
        if (me.name().equals(name) && me.signature().equals(signature)
            && me.inlineTags().tags() != null && !me.inlineTags().tags().isEmpty()
            && notStrippable.contains(me.containingClass())) {
          return me;
        }
      }
    }
    return null;
  }

  public MethodInfo findSuperclassImplementation(Set<ClassInfo> notStrippable) {
    if (mReturnType == null) {
      // ctor
      return null;
    }
    if (mOverriddenMethod != null) {
      // Even if we're told outright that this was the overridden method, we want to
      // be conservative and ignore mismatches of parameter types -- they arise from
      // extending generic specializations, and we want to consider the derived-class
      // method to be a non-override.
      if (this.signature().equals(mOverriddenMethod.signature())) {
        return mOverriddenMethod;
      }
    }

    ArrayList<ClassInfo> queue = new ArrayList<ClassInfo>();
    if (containingClass().realSuperclass() != null
        && containingClass().realSuperclass().isAbstract()) {
      queue.add(containingClass());
    }
    addInterfaces(containingClass().realInterfaces(), queue);
    for (ClassInfo iface : queue) {
      for (MethodInfo me : iface.getMethods()) {
        if (me.name().equals(this.name()) && me.signature().equals(this.signature())
            && notStrippable.contains(me.containingClass())) {
          return me;
        }
      }
    }
    return null;
  }

  private class FirstSentenceTags implements InheritedTags {
    public List<TagInfo> tags() {
      return comment().briefTags();
    }

    public InheritedTags inherited() {
      MethodInfo m = findOverriddenMethod(name(), signature());
      if (m != null) {
        return m.firstSentenceTags();
      } else {
        return null;
      }
    }
  }

  private class ReturnTags implements InheritedTags {
    public List<TagInfo> tags() {
      return comment().returnTags();
    }

    public InheritedTags inherited() {
      MethodInfo m = findOverriddenMethod(name(), signature());
      if (m != null) {
        return m.returnTags();
      } else {
        return null;
      }
    }
  }

  public boolean isDeprecated() {
    if (!mDeprecatedKnown) {
      boolean commentDeprecated = comment().isDeprecated();
      boolean annotationDeprecated = false;
      for (AnnotationInstanceInfo annotation : annotations()) {
        if (annotation.type().qualifiedName().equals("java.lang.Deprecated")) {
          annotationDeprecated = true;
          break;
        }
      }

      if (commentDeprecated != annotationDeprecated) {
        Errors.error(Errors.DEPRECATION_MISMATCH, position(), "Method "
            + mContainingClass.qualifiedName() + "." + name()
            + ": @Deprecated annotation and @deprecated doc tag do not match");
      }

      mIsDeprecated = commentDeprecated | annotationDeprecated;
      mDeprecatedKnown = true;
    }
    return mIsDeprecated;
  }
  
  public void setDeprecated(boolean deprecated) {
    mDeprecatedKnown = true;
    mIsDeprecated = deprecated;
  }

  public List<TypeInfo> getTypeParameters() {
    return mTypeParameters;
  }

  @Override protected MethodInfo clone() {
    try {
      return (MethodInfo) super.clone();
    } catch (CloneNotSupportedException e) {
      throw new AssertionError();
    }
  }

  public MethodInfo cloneForClass(ClassInfo newContainingClass) {
    MethodInfo result = clone();
    result.setContainingClass(newContainingClass);
    return result;
  }

  public MethodInfo(String rawCommentText, List<TypeInfo> typeParameters, String name,
      String signature, ClassInfo containingClass, ClassInfo realContainingClass, boolean isPublic,
      boolean isProtected, boolean isPackagePrivate, boolean isPrivate, boolean isFinal,
      boolean isStatic, boolean isSynthetic, boolean isAbstract, boolean isSynchronized,
      boolean isNative, String kind, String flatSignature,
      MethodInfo overriddenMethod, TypeInfo returnType, List<ParameterInfo> parameters,
      List<ClassInfo> thrownExceptions, SourcePositionInfo position,
      AnnotationInstanceInfo[] annotations) {
    // Explicitly coerce 'final' state of Java6-compiled enum values() method, to match
    // the Java5-emitted base API description.
    super(rawCommentText, name, signature, containingClass, realContainingClass, isPublic,
        isProtected, isPackagePrivate, isPrivate, ((name.equals("values") && containingClass
            .isEnum()) ? true : isFinal), isStatic, isSynthetic, kind, position, annotations);

    // The underlying MethodDoc for an interface's declared methods winds up being marked
    // non-abstract. Correct that here by looking at the immediate-parent class, and marking
    // this method abstract if it is an unimplemented interface method.
    if (containingClass.isInterface()) {
      isAbstract = true;
    }

    mTypeParameters = typeParameters;
    mIsAbstract = isAbstract;
    mIsSynchronized = isSynchronized;
    mIsNative = isNative;
    mFlatSignature = flatSignature;
    mOverriddenMethod = overriddenMethod;
    mReturnType = returnType;
    mParameters = parameters;
    mThrownExceptions = thrownExceptions;
  }

  public void init(AnnotationValueInfo defaultAnnotationElementValue) {
    mDefaultAnnotationElementValue = defaultAnnotationElementValue;
  }

  @Override public void initVisible(Project project) {
    super.initVisible(project);

    List<ThrowsTagInfo> throwsTags = new ArrayList<ThrowsTagInfo>();
    throwsTags.addAll(comment().throwsTags());
    for (ClassInfo classInfo : mThrownExceptions) {
      if (!inList(classInfo, throwsTags)) {
        throwsTags.add(new ThrowsTagInfo("@throws", "@throws", classInfo.qualifiedName(),
            classInfo, "", containingClass(), position()));
      }
    }

    mThrowsTags = ImmutableList.copyOf(throwsTags);
    mParamTags = computeParamTags();

    for (TagInfo tagInfo : Iterables.concat(throwsTags, Arrays.asList(mParamTags))) {
      tagInfo.initVisible(project);
    }
  }

  public ParamTagInfo[] computeParamTags() {
    String[] names = new String[mParameters.size()];
    String[] comments = new String[mParameters.size()];
    SourcePositionInfo[] positions = new SourcePositionInfo[mParameters.size()];

    // get the right names so we can handle our names being different from
    // our parent's names.
    int i = 0;
    for (ParameterInfo p : mParameters) {
      names[i] = p.name();
      comments[i] = "";
      positions[i] = p.position();
      i++;
    }

    // gather our comments, and complain about misnamed @param tags
    for (ParamTagInfo tag : comment().paramTags()) {
      int index = indexOfParam(tag.parameterName(), names);
      if (index >= 0) {
        comments[index] = tag.parameterComment();
        positions[index] = tag.position();
      } else {
        Errors.error(Errors.UNKNOWN_PARAM_TAG_NAME, tag.position(),
            "@param tag with name that doesn't match the parameter list: '" + tag.parameterName()
                + "'");
      }
    }

    // get our parent's tags to fill in the blanks
    MethodInfo overridden = findOverriddenMethod(name(), signature());
    if (overridden != null) {
      ParamTagInfo[] maternal = overridden.computeParamTags();
      for (i = 0; i < mParameters.size(); i++) {
        if (comments[i].equals("")) {
          comments[i] = maternal[i].parameterComment();
          positions[i] = maternal[i].position();
        }
      }
    }

    // construct the results, and cache them for next time
    ParamTagInfo[] result = new ParamTagInfo[mParameters.size()];
    for (i = 0; i < mParameters.size(); i++) {
      result[i] = new ParamTagInfo("@param", "@param", names[i] + " " + comments[i], parent(),
              positions[i]);

      // while we're here, if we find any parameters that are still undocumented at this
      // point, complain. (this warning is off by default, because it's really, really
      // common; but, it's good to be able to enforce it)
      if (comments[i].equals("")) {
        Errors.error(Errors.UNDOCUMENTED_PARAMETER, positions[i], "Undocumented parameter '"
            + names[i] + "' on method '" + name() + "'");
      }
    }

    return result;
  }

  public boolean isAbstract() {
    return mIsAbstract;
  }

  public boolean isSynchronized() {
    return mIsSynchronized;
  }

  public boolean isNative() {
    return mIsNative;
  }

  public String flatSignature() {
    return mFlatSignature;
  }

  public InheritedTags inlineTags() {
    return new InlineTags();
  }

  public InheritedTags firstSentenceTags() {
    return new FirstSentenceTags();
  }

  public InheritedTags returnTags() {
    return new ReturnTags();
  }

  public TypeInfo returnType() {
    return mReturnType;
  }
  
  public String prettySignature() {
    return name() + prettyParameters();
  }
  
  /**
   * Returns a printable version of the parameters of this method's signature.
   */
  public String prettyParameters() {
    StringBuilder params = new StringBuilder("(");
    for (ParameterInfo pInfo : mParameters) {
      if (params.length() > 1) {
        params.append(",");
      }
      params.append(pInfo.type().simpleTypeName());
    }
    
    params.append(")");
    return params.toString();
  }

  /**
   * Returns a name consistent with the {@link com.google.doclava.MethodInfo#getHashableName()}.
   */
  public String getHashableName() {
    StringBuilder result = new StringBuilder();
    result.append(name());
    for (Iterator<ParameterInfo> p = mParameters.iterator(); p.hasNext(); ) {
      ParameterInfo parameterInfo = p.next();
      result.append(":");
      if (!p.hasNext() && isVarArgs()) {
        // TODO: note that this does not attempt to handle vararg methods whose
        // last parameter is a list of arrays, e.g. "Object[]...".
        result.append(parameterInfo.type().fullNameNoDimension(typeVariables())).append("...");
      } else {
        result.append(parameterInfo.type().fullName(typeVariables()));
      }
    }
    return result.toString();
  }

  private boolean inList(ClassInfo item, List<ThrowsTagInfo> list) {
    int len = list.size();
    String qn = item.qualifiedName();
    for (int i = 0; i < len; i++) {
      ClassInfo ex = list.get(i).exception();
      if (ex != null && ex.qualifiedName().equals(qn)) {
        return true;
      }
    }
    return false;
  }

  public List<ThrowsTagInfo> throwsTags() {
    if (mThrowsTags == null) {
      throw new IllegalStateException("Call initVisible() first");
    }
    return mThrowsTags;
  }

  private static int indexOfParam(String name, String[] list) {
    final int N = list.length;
    for (int i = 0; i < N; i++) {
      if (name.equals(list[i])) {
        return i;
      }
    }
    return -1;
  }

  public ParamTagInfo[] paramTags() {
    if (mParamTags == null) {
      throw new IllegalStateException("Call initVisible() first!");
    }
    return mParamTags;
  }

  public List<SeeTagInfo> seeTags() {
    List<SeeTagInfo> result = comment().seeTags();
    if (result == null) {
      if (mOverriddenMethod != null) {
        result = mOverriddenMethod.seeTags();
      }
    }
    return result;
  }

  public List<TagInfo> deprecatedTags() {
    List<TagInfo> result = comment().deprecatedTags();
    if (result.isEmpty()) {
      if (comment().undeprecateTags().isEmpty()) {
        if (mOverriddenMethod != null) {
          result = mOverriddenMethod.deprecatedTags();
        }
      }
    }
    return result;
  }

  public List<ParameterInfo> parameters() {
    return mParameters;
  }

  public boolean matchesParams(String[] params, String[] dimensions, boolean varargs) {
    List<ParameterInfo> mine = mParameters;
    int len = mine.size();
    if (len != params.length) {
      return false;
    }
    for (int i = 0; i < len; i++) {
      if (!mine.get(i).matchesDimension(dimensions[i], varargs)) {
        return false;
      }
      TypeInfo myType = mine.get(i).type();
      String qualifiedName = myType.qualifiedTypeName();
      String realType = myType.isPrimitive() ? "" : myType.asClassInfo().qualifiedName();
      String s = params[i];

      // Check for a matching generic name or best known type
      if (!matchesType(qualifiedName, s) && !matchesType(realType, s)) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Returns true if a parameter from a method signature is
   * compatible with a parameter given in a {@code @link} tag.
   */
  private boolean matchesType(String signatureParam, String callerParam) {
    int signatureLength = signatureParam.length();
    int callerLength = callerParam.length();
    return signatureParam.equals(callerParam) || ((callerLength + 1) < signatureLength
        && signatureParam.charAt(signatureLength - callerLength - 1) == '.'
        && signatureParam.endsWith(callerParam));
  }

  public void makeHDF(Data data, String base) {
    data.setValue(base + ".kind", kind());
    data.setValue(base + ".name", name());
    data.setValue(base + ".href", htmlPage());
    data.setValue(base + ".anchor", anchor());

    if (mReturnType != null) {
      returnType().makeHDF(data, base + ".returnType", false, typeVariables());
      data.setValue(base + ".abstract", mIsAbstract ? "abstract" : "");
    }

    data.setValue(base + ".synchronized", mIsSynchronized ? "synchronized" : "");
    data.setValue(base + ".final", isFinal() ? "final" : "");
    data.setValue(base + ".static", isStatic() ? "static" : "");

    TagInfo.makeHDF(data, base + ".shortDescr", firstSentenceTags());
    TagInfo.makeHDF(data, base + ".descr", inlineTags());
    TagInfo.makeHDF(data, base + ".deprecated", deprecatedTags());
    TagInfo.makeHDF(data, base + ".seeAlso", seeTags());
    data.setValue(base + ".since.key", SinceTagger.keyForName(getSince()));
    data.setValue(base + ".since.name", getSince());
    ParamTagInfo.makeHDF(data, base + ".paramTags", paramTags());
    AttrTagInfo.makeReferenceHDF(data, base + ".attrRefs", comment().attrTags());
    ThrowsTagInfo.makeHDF(data, base + ".throws", throwsTags());
    ParameterInfo.makeHDF(data, base + ".params", parameters(), isVarArgs(), typeVariables());
    if (isProtected()) {
      data.setValue(base + ".scope", "protected");
    } else if (isPublic()) {
      data.setValue(base + ".scope", "public");
    }
    TagInfo.makeHDF(data, base + ".returns", returnTags());

    if (mTypeParameters != null) {
      TypeInfo.makeHDF(data, base + ".generic.typeArguments", mTypeParameters, false);
    }
    
    setFederatedReferences(data, base);
  }

  public HashSet<String> typeVariables() {
    HashSet<String> result = TypeInfo.typeVariables(mTypeParameters);
    ClassInfo cl = containingClass();
    while (cl != null) {
      List<TypeInfo> types = cl.asTypeInfo().typeArguments();
      if (types != null) {
        TypeInfo.typeVariables(types, result);
      }
      cl = cl.containingClass();
    }
    return result;
  }

  @Override
  public boolean isExecutable() {
    return true;
  }

  public List<ClassInfo> thrownExceptions() {
    return mThrownExceptions;
  }

  public String typeArgumentsName(HashSet<String> typeVars) {
    if (mTypeParameters == null || mTypeParameters.isEmpty()) {
      return "";
    } else {
      return TypeInfo.typeArgumentsName(mTypeParameters, typeVars);
    }
  }

  public AnnotationValueInfo defaultAnnotationElementValue() {
    return mDefaultAnnotationElementValue;
  }

  public void setVarargs(boolean set) {
    mIsVarargs = set;
  }

  public boolean isVarArgs() {
    return mIsVarargs;
  }

  @Override
  public String toString() {
    return this.name();
  }

  public void addException(String exec) {
    mThrownExceptions.add(new ClassInfo(exec));
  }
  
  public void addParameter(ParameterInfo p) {
    mParameters.add(p);
    mTypeParameters.add(p.type());
  }

  private String mFlatSignature;
  private MethodInfo mOverriddenMethod;
  private TypeInfo mReturnType;
  private boolean mIsAbstract;
  private boolean mIsSynchronized;
  private boolean mIsNative;
  private boolean mIsVarargs;
  private boolean mDeprecatedKnown;
  private boolean mIsDeprecated;
  private List<ParameterInfo> mParameters;
  private List<ClassInfo> mThrownExceptions;
  private List<ThrowsTagInfo> mThrowsTags;
  private ParamTagInfo[] mParamTags;
  private List<TypeInfo> mTypeParameters;
  private AnnotationValueInfo mDefaultAnnotationElementValue;

  // TODO: merge with droiddoc version (above)  
  public String qualifiedName() {
    String parentQName = (containingClass() != null)
        ? (containingClass().qualifiedName() + ".") : "";
    return parentQName + name();
  }

  @Override
  public String signature() {
    if (mSignature == null) {
      StringBuilder params = new StringBuilder("(");
      for (ParameterInfo pInfo : mParameters) {
        if (params.length() > 1) {
          params.append(", ");
        }
        params.append(pInfo.type().fullName());
      }
      
      params.append(")");
      mSignature = params.toString();
    }
    return mSignature;
  }

  public boolean matches(MethodInfo other) {
    return prettySignature().equals(other.prettySignature());
  }

  public boolean throwsException(ClassInfo exception) {
    for (ClassInfo e : mThrownExceptions) {
      if (e.qualifiedName().equals(exception.qualifiedName())) {
        return true;
      }
    }
    return false;
  }
  
  public boolean isConsistent(MethodInfo mInfo) {
    boolean consistent = true;
    if (!this.mReturnType.equals(mInfo.mReturnType)) {
      consistent = false;
      Errors.error(Errors.CHANGED_TYPE, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed return type from " + mReturnType + " to " + mInfo.mReturnType);
    }

    if (mIsAbstract != mInfo.mIsAbstract) {
      consistent = false;
      Errors.error(Errors.CHANGED_ABSTRACT, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed 'abstract' qualifier");
    }

    if (mIsNative != mInfo.mIsNative) {
      consistent = false;
      Errors.error(Errors.CHANGED_NATIVE, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed 'native' qualifier");
    }

    if (mIsFinal != mInfo.mIsFinal) {
      // Compiler-generated methods vary in their 'final' qual between versions of
      // the compiler, so this check needs to be quite narrow. A change in 'final'
      // status of a method is only relevant if (a) the method is not declared 'static'
      // and (b) the method's class is not itself 'final'.
      if (!mIsStatic) {
        if ((containingClass() == null) || (!containingClass().isFinal())) {
          consistent = false;
          Errors.error(Errors.CHANGED_FINAL, mInfo.position(), "Method " + mInfo.qualifiedName()
              + " has changed 'final' qualifier");
        }
      }
    }

    if (mIsStatic != mInfo.mIsStatic) {
      consistent = false;
      Errors.error(Errors.CHANGED_STATIC, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed 'static' qualifier");
    }

    if (!scope().equals(mInfo.scope())) {
      consistent = false;
      Errors.error(Errors.CHANGED_SCOPE, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " changed scope from " + scope() + " to " + mInfo.scope());
    }

    if (!isDeprecated() == mInfo.isDeprecated()) {
      Errors.error(Errors.CHANGED_DEPRECATED, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed deprecation state");
      consistent = false;
    }

    if (mIsSynchronized != mInfo.mIsSynchronized) {
      Errors.error(Errors.CHANGED_SYNCHRONIZED, mInfo.position(), "Method " + mInfo.qualifiedName()
          + " has changed 'synchronized' qualifier from " + mIsSynchronized + " to "
          + mInfo.mIsSynchronized);
      consistent = false;
    }

    for (ClassInfo exception : thrownExceptions()) {
      if (!mInfo.throwsException(exception)) {
        // exclude 'throws' changes to finalize() overrides with no arguments
        if (!name().equals("finalize") || !mParameters.isEmpty()) {
          Errors.error(Errors.CHANGED_THROWS, mInfo.position(), "Method " + mInfo.qualifiedName()
              + " no longer throws exception " + exception.qualifiedName());
          consistent = false;
        }
      }
    }

    for (ClassInfo exec : mInfo.thrownExceptions()) {
      // exclude 'throws' changes to finalize() overrides with no arguments
      if (!throwsException(exec)) {
        if (!name().equals("finalize") || !mParameters.isEmpty()) {
          Errors.error(Errors.CHANGED_THROWS, mInfo.position(), "Method " + mInfo.qualifiedName()
              + " added thrown exception " + exec.qualifiedName());
          consistent = false;
        }
      }
    }

    return consistent;
  }
}