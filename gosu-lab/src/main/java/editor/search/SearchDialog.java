package editor.search;

import editor.FileTree;
import editor.FileTreeUtil;
import editor.GosuPanel;
import editor.NodeKind;
import editor.RunMe;
import editor.Scheme;
import editor.util.AbstractDialog;
import editor.util.DirectoryEditor;
import editor.util.ModalEventQueue;
import editor.util.ProgressFeedback;
import gw.lang.reflect.json.IJsonIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

/**
 */
public class SearchDialog extends AbstractDialog
{
  private static State STATE = null;


  private final FileTree _searchDir;
  private final boolean _bReplace;
  private JComboBox<String> _cbSearch;
  private JComboBox<String> _cbReplace;
  private JCheckBox _checkCase;
  private JCheckBox _checkWords;
  private JCheckBox _checkRegex;
  private JRadioButton _rbProject;
  private JRadioButton _rbDirectory;
  private DirectoryEditor _cbDir;
  private JRadioButton _rbScope;
  private JComboBox _cbScope;
  private JCheckBox _checkFileMask;
  private JComboBox<String> _cbFileMasks;
  private DialogStateHandler _stateHandler;

  public SearchDialog( FileTree searchDir )
  {
    this( searchDir, false );
  }
  public SearchDialog( FileTree searchDir, boolean bReplace )
  {
    super( RunMe.getEditorFrame(), bReplace ? "Replace in Path" : "Find in Path", true );
    _searchDir = searchDir;
    _bReplace = bReplace;
    configUi();
  }

  private void configUi()
  {
    JComponent contentPane = (JComponent)getContentPane();
    contentPane.setBorder( BorderFactory.createEmptyBorder( 8, 8, 8, 8 ) );
    contentPane.setLayout( new BorderLayout() );

    _stateHandler = new DialogStateHandler();
    
    JPanel mainPanel = new JPanel( new BorderLayout() );
    mainPanel.setBorder( BorderFactory.createCompoundBorder( UIManager.getBorder( "TextField.border" ),
                                                             BorderFactory.createEmptyBorder( 5, 5, 5, 5 ) ) );
    mainPanel.add( makeSearchPanel(), BorderLayout.CENTER );

    contentPane.add( mainPanel, BorderLayout.CENTER );

    JPanel south = new JPanel( new BorderLayout() );
    south.setBorder( BorderFactory.createEmptyBorder( 4, 0, 0, 0 ) );
    JPanel filler = new JPanel();
    south.add( filler, BorderLayout.CENTER );

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout( new BoxLayout( buttonPanel, BoxLayout.X_AXIS ) );

    JButton btnFind = new JButton( _bReplace ? "Replace" : "Find" );
    btnFind.setMnemonic( 'F' );
    btnFind.addActionListener( e -> find() );
    buttonPanel.add( btnFind );
    getRootPane().setDefaultButton( btnFind );

    JButton btnCancel = new JButton( "Cancel" );
    btnCancel.addActionListener( e -> close() );
    buttonPanel.add( btnCancel );

    south.add( buttonPanel, BorderLayout.EAST );
    contentPane.add( south, BorderLayout.SOUTH );

    mapCancelKeystroke();

    setSize( 400, _bReplace ? 420 : 400 );

    StudioUtilities.centerWindowInFrame( this, getOwner() );

    EventQueue.invokeLater(
      () -> {
        applyState();
        if( _searchDir != FileTreeUtil.getRoot() )
        {
          _rbDirectory.setSelected( true );
          _cbDir.setText( _searchDir.getFileOrDir().getAbsolutePath() );
        }
        else
        {
          _rbProject.setSelected( true );
        }
        _stateHandler.actionPerformed( null );
        _cbSearch.requestFocus();
      } );

  }

  private void applyState()
  {
    if( STATE != null )
    {
      STATE.restore( this );
    }
  }

  private void find()
  {
    STATE = new State().save( this );

    close();

    GosuPanel gosuPanel = RunMe.getEditorFrame().getGosuPanel();
    SearchPanel searchPanel = clearAndShowSearchPanel( gosuPanel );
    searchPanel.showReplace( _bReplace );
    searchPanel.setReplacePattern( (String)_cbReplace.getSelectedItem() );

    FileTree root = FileTreeUtil.getRoot();
    boolean[] bFinished = {false};
    ProgressFeedback.runWithProgress( "Searching...",
                                      progress -> {
                                        EventQueue.invokeLater( () -> {
                                          progress.setLength( root.getTotalFiles() );

                                          addReplaceInfo( searchPanel );

                                          String text = (String)_cbSearch.getSelectedItem();
                                          SearchTree results = new SearchTree( "<html><b>$count</b> occurrences of <b>'" + text + "'</b> in " + getScopeName(), NodeKind.Directory, SearchTree.empty() );
                                          searchPanel.add( results );

                                          FileTreeSearcher searcher = new FileTreeSearcher( text, !_checkCase.isSelected(), _checkWords.isSelected(), _checkRegex.isSelected() );
                                          searcher.searchTree( getSearchDir(), results, ft -> include( ft, getFileMatchRegex() ), progress );
                                          selectFirstMatch( results );
                                          bFinished[0] = true;
                                        } );
                                      } );
    new ModalEventQueue( () -> !bFinished[0] ).run();
  }

  private SearchPanel clearAndShowSearchPanel( GosuPanel gosuPanel )
  {
    SearchPanel searchPanel = gosuPanel.getSearchPanel();
    if( searchPanel != null )
    {
      searchPanel.clear();
    }
    gosuPanel.showSearches( true );
    return gosuPanel.getSearchPanel();
  }

  private void addReplaceInfo( SearchPanel searchPanel )
  {
    if( _bReplace )
    {
      String text = (String)_cbReplace.getSelectedItem();
      SearchTree results = new SearchTree( "<html>Replace occurrences with <b>'" + text + "'</b>", NodeKind.Info, SearchTree.empty() );
      searchPanel.add( results );
    }
  }

  private void selectFirstMatch( SearchTree results )
  {
    if( results.getChildCount() == 0 )
    {
      results.select();
    }
    else
    {
      selectFirstMatch( (SearchTree)results.getChildAt( 0 ) );
    }
  }

  private List<String> getFileMatchRegex()
  {
    if( _checkFileMask.isSelected() )
    {
      String mask = (String)_cbFileMasks.getSelectedItem();
      if( mask != null && mask.isEmpty() )
      {
        List<String> list = new ArrayList<>();
        for( StringTokenizer tok = new StringTokenizer( mask, ";" ); tok.hasMoreTokens(); )
        {
          String ext = tok.nextToken().trim();
          list.add( StringUtil.wildcardToRegex( ext ) );
        }
        return list;
      }
    }
    return Collections.emptyList();
  }

  private boolean include( FileTree ft, List<String> fileMatchRegex )
  {
    if( !fileMatchRegex.isEmpty() )
    {
      for( String regex : fileMatchRegex )
      {
        if( ft.getName().toLowerCase().matches( regex ) )
        {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private String getScopeName()
  {
    if( _rbProject.isSelected() )
    {
      return "Experiment";
    }
    if( _rbDirectory.isSelected() )
    {
      return _cbDir.getText();
    }
    if( _rbScope.isSelected() )
    {
      return (String)_cbScope.getSelectedItem();
    }

    throw new IllegalStateException();
  }

  private JComponent makeSearchPanel()
  {
    JPanel configPanel = new JPanel( new GridBagLayout() );
    configPanel.setBorder( BorderFactory.createEmptyBorder( 10, 10, 10, 10 ) );

    final GridBagConstraints c = new GridBagConstraints();

    int iY = 0;

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 0, 0, 0, 0 );
    JLabel label = new JLabel( "Text to find:" );
    configPanel.add( label, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 1;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 0, 0, 5, 0 );
    _cbSearch = new JComboBox<>();
    _cbSearch.setEditable( true );
    configPanel.add( _cbSearch, c );


    _cbReplace = new JComboBox<>();
    _cbReplace.setEditable( true );
    if( _bReplace )
    {
      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.NONE;
      c.gridx = 0;
      c.gridy = iY;
      c.gridwidth = 1;
      c.gridheight = 1;
      c.weightx = 0;
      c.weighty = 0;
      c.insets = new Insets( 0, 0, 0, 0 );
      label = new JLabel( "Replace with:" );
      configPanel.add( label, c );

      c.anchor = GridBagConstraints.WEST;
      c.fill = GridBagConstraints.HORIZONTAL;
      c.gridx = 1;
      c.gridy = iY++;
      c.gridwidth = GridBagConstraints.REMAINDER;
      c.gridheight = 1;
      c.weightx = 1;
      c.weighty = 0;
      c.insets = new Insets( 0, 0, 0, 0 );
      configPanel.add( _cbReplace, c );
    }


    //---------------------------------------------------------------------------------------
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 5, 0 );
    JPanel separator = new JPanel();
    separator.setBorder( BorderFactory.createMatteBorder( 1, 0, 0, 0, Scheme.active().getControlShadow() ) );
    configPanel.add( separator, c );


    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 0, 0, 0, 0 );
    _checkCase = new JCheckBox( "Case sensitive" );
    _checkCase.setMnemonic( 'C' );
    configPanel.add( _checkCase, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _checkWords = new JCheckBox( "Whole words only" );
    _checkWords.setMnemonic( 'R' );
    configPanel.add( _checkWords, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _checkRegex = new JCheckBox( "Regular expression" );
    _checkRegex.setMnemonic( 'G' );
    _checkRegex.addActionListener( _stateHandler );
    configPanel.add( _checkRegex, c );

    //-----------------------------------------------------------------------------------
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 5, 0 );
    separator = new JPanel();
    separator.setBorder( BorderFactory.createMatteBorder( 1, 0, 0, 0, Scheme.active().getControlShadow() ) );
    configPanel.add( separator, c );


    ButtonGroup group = new ButtonGroup();

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 0, 0, 0, 0 );
    _rbProject = new JRadioButton( "Whole experiment" );
    _rbProject.setMnemonic( 'H' );
    _rbProject.addActionListener( _stateHandler );
    group.add( _rbProject );
    configPanel.add( _rbProject, c );


    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _rbDirectory = new JRadioButton( "Directory:" );
    _rbDirectory.setMnemonic( 'D' );
    _rbDirectory.addActionListener( _stateHandler );
    group.add( _rbDirectory );
    configPanel.add( _rbDirectory, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 1;
    c.gridy = iY++;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _cbDir = new DirectoryEditor( "Directory", _searchDir.getFileOrDir().getAbsolutePath(), RunMe::getEditorFrame );
    configPanel.add( _cbDir, c );


    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _rbScope = new JRadioButton( "Scope:" );
    _rbScope.setMnemonic( 'S' );
    _rbScope.addActionListener( _stateHandler );
    group.add( _rbScope);
    configPanel.add( _rbScope, c );

    _rbProject.setSelected( true );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 1;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _cbScope = new JComboBox();
    configPanel.add( _cbScope, c );

    //-----------------------------------------------------------------------------------
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 0;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 5, 0 );
    separator = new JPanel();
    separator.setBorder( BorderFactory.createMatteBorder( 1, 0, 0, 0, Scheme.active().getControlShadow() ) );
    configPanel.add( separator, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.NONE;
    c.gridx = 0;
    c.gridy = iY;
    c.gridwidth = 1;
    c.gridheight = 1;
    c.weightx = 0;
    c.weighty = 0;
    c.insets = new Insets( 0, 0, 0, 0 );
    _checkFileMask = new JCheckBox( "File mask(s):" );
    _checkFileMask.setMnemonic( 'M' );
    _checkFileMask.addActionListener( _stateHandler );
    configPanel.add( _checkFileMask, c );

    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.gridx = 1;
    c.gridy = iY++;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridheight = 1;
    c.weightx = 1;
    c.weighty = 0;
    c.insets = new Insets( 5, 0, 0, 0 );
    _cbFileMasks= new JComboBox();
    _cbFileMasks.setEditable( true );
    configPanel.add( _cbFileMasks, c );

    return configPanel;
  }

  public FileTree getSearchDir()
  {
    if( _rbDirectory.isSelected() )
    {
      File file = new File( _cbDir.getText() );
      if( file.exists() )
      {
        FileTree fileTree = FileTreeUtil.getRoot().find( file );
        if( fileTree != null )
        {
          return fileTree;
        }
      }
    }
    return FileTreeUtil.getRoot();
  }

  private class DialogStateHandler implements ActionListener
  {
    @Override
    public void actionPerformed( ActionEvent e )
    {
      _checkWords.setEnabled( !_checkRegex.isSelected() );
      _cbDir.setEnabled( _rbDirectory.isSelected() );
      _cbScope.setEnabled( _rbScope.isSelected() );
      _cbDir.setEnabled( _rbDirectory.isSelected() );
      _cbFileMasks.setEnabled( _checkFileMask.isSelected() );
    }
  }

  public static class State implements IJsonIO
  {
    String[] _searchHistory;
    String[] _replaceHistory;

    boolean _case;
    boolean _words;
    boolean _regex;

    boolean _project;
    boolean _dir;
    boolean _scope;

    String _selectedDir;

    boolean _mask;
    String _selectedMask;
    String[] _masks;

    public State()
    {
    }

    public State save( SearchDialog dlg )
    {
      _searchHistory = makeArray( dlg._cbSearch );
      _replaceHistory = makeArray(dlg. _cbReplace );

      _case = dlg._checkCase.isSelected();
      _words = dlg._checkWords.isSelected();
      _regex = dlg._checkRegex.isSelected();

      _project = dlg._rbProject.isSelected();
      _dir = dlg._rbDirectory.isSelected();
      _scope = dlg._rbScope.isSelected();

      _selectedDir = dlg._cbDir.getText();

      _mask = dlg._checkFileMask.isSelected();
      _selectedMask = (String)dlg._cbFileMasks.getSelectedItem();
      _masks = makeArray( dlg._cbFileMasks );

      return this;
    }

    public void restore( SearchDialog dlg )
    {
      dlg._cbSearch.setModel( new DefaultComboBoxModel<>( _searchHistory ) );
      dlg._cbSearch.getEditor().setItem( null );
      dlg._cbReplace.setModel( new DefaultComboBoxModel<>( _replaceHistory ) );
      dlg._cbReplace.getEditor().setItem( null );

      dlg._checkCase.setSelected( _case );
      dlg._checkWords.setSelected( _words );
      dlg._checkRegex.setSelected( _regex );

      dlg._rbProject.setSelected( _project );
      dlg._rbDirectory.setSelected( _dir );
      dlg._rbScope.setSelected( _scope );

      dlg._cbDir.setText( _selectedDir );

      dlg._checkFileMask.setSelected( _mask );
      dlg._cbFileMasks.setSelectedItem( _selectedMask );
      dlg._cbFileMasks.setModel( new DefaultComboBoxModel<>( _masks ) );
    }

    private String[] makeArray( JComboBox<String> cb )
    {
      DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>)cb.getModel();
      int size = Math.min( 20, model.getSize() );
      String selectedItem = (String)cb.getSelectedItem();
      int extra = 0;
      if( selectedItem != null && !selectedItem.isEmpty() && model.getIndexOf( selectedItem ) < 0  )
      {
        extra = 1;
      }
      String[] array = new String[size+extra];
      if( extra > 0 )
      {
        array[0] = selectedItem;
      }
      for( int i = 0; i < size; i++ )
      {
        array[i+extra] = model.getElementAt( i );
      }
      return array;
    }
  }
}
