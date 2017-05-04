package gw.lang.reflect.gs;

import gw.fs.IFile;
import gw.lang.reflect.IFileConnected;
import gw.lang.reflect.ITypeLoader;
import java.util.Collection;
import java.util.Set;

/**
 */
public interface ISourceProducer extends IFileConnected
{


  /**
   * Supported kinds of source.
   */
  enum SourceKind
  {
    Java,
    Gosu;
  }

  /**
   * The TypeLoader to which this producer is scoped
   */
  ITypeLoader getTypeLoader();

  /**
   * What kind of source is produced?  Java or Gosu?
   */
  SourceKind getSourceKind();

  /**
   * The file extensions this producer handles (no dot).
   */
  Set<String> getExtensions();

  /**
   * Does this producer supply source for the specified fqn?
   */
  boolean isType( String fqn );
  boolean isTopLevelType( String fqn );

  /**
   * Verifies whether or not the specified package may be provided by this source producer
   */
  boolean isPackage( String pkg );

  /**
   * What kind of type corresponds with fqn?
   */
  ClassType getClassType( String fqn );

  /**
   * What is the package name corresonding with fqn?
   */
  String getPackage( String fqn );

  /**
   * Produce source corresponding with the fqn.
   */
  String produce( String fqn );

  Collection<String> getAllTypeNames();
  Collection<TypeName> getTypeNames( String namespace );

  IFile findFileForType( String fqn );

  /**
   * Clear all cached data
   */
  void clear();
}