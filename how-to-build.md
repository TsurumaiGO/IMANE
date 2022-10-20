# IMANE-PCビルド手順

## openjdkのインストール
 - https://jdk.java.net/ からアーカイブをダウンロードし任意のディレクトリに展開  
   ※ JDK8以降で動作可。JDK9以降で大きな非互換があるが、今後に備える意味で10以降を推奨。長期サポート版の選択肢がある最新版はJDK11。
 - 環境変数JAVA_HOMEにインストールしたopenjdkのパスを設定  
   ※ Oracle JREをインストールしている場合は、PATH環境変数を上書きされないよう注意  

 - 確認  
   コマンドプロンプトから以下のコマンドを実行し、インストールしたバージョンの情報が出力されることを確認  
	> java -version
 
## eclipseのインストール
 - https://www.eclipse.org/downloads/packages/ から Eclipse IDE for Enterprise Java and Web Developersをダウンロードし、任意のディレクトリに展開
 - [インストール先]\eclipse.iniをテキストエディタで開き、-vmオプションに[openjdkインストール先]\binを設定  
   ※ 初期状態ではeclipse同梱のJREが設定されている
 - [インストール先]\eclipse.exeを実行

## tomcat 9.0のインストール
 - https://tomcat.apache.org/download-90.cgi からtomcat 9.0系アーカイブをダウンロードし任意のディレクトリに展開  
   ※ Windowsインストーラ形式は複数バージョンの併用・切り替え動作に問題があるため、開発用にはアーカイブ形式を使った手動インストールを推奨  
   ※ tomcat 9.0以前と10.0ではソースレベルでの非互換があり、現時点で10.0系は動作不可
 - [インストール先]\bin\startup.batを実行し、サーバプロセスが起動すること、デフォルトページ( http://localhost:8080/ )が表示されることを確認
 - (オプション) 管理UI(tomcat-manager)を有効化  
   手順は割愛

## eclipseの設定
 - JDKの設定  
  メニュー > Window > Preference > Java > Installed JREにインストールしたopenjdkが設定されていることを確認(なければ追加)
 - mavenの設定
   - メニュー　>　Window > Preference > Maven > User Settings > Local Repositoryにローカルリポジトリ保存先ディレクトリを指定  
   ※ 大量のファイルがコピーされるため、空き容量に余裕のあるドライブを指定
 - tomcatサーバの設定
   - メニュー > Window > Preference > Server > Runtime Environments
   - Add　>　サーバの一覧からtomcat 9.0を選択
     - Tomcat installed directory: >tomcatインストール先ディレクトリを指定
     - JRE: >先にインストールしたJDKを選択
   - メニュー　>　Window > Show View > Serversビューを開く
   - Serversビューからtomcatサーバの起動、停止ができることを確認

  ※ eclipse本体、eclipseプロジェクト、tomcatのそれぞれに使用するJREを設定する項目があり、異なるJREが混在するとトラブルの原因になる。

## GitHubリポジトリのセットアップ
 - eclipse > メニュー > Window > Show View > Git Repositoriesビューを開く
 - https://github.com/TsurumaiGO/IMANE をリモートリポジトリに追加
   - 上記URLをクリップボードにコピーし、Git Repositoriesビューに貼り付け(Ctrl+V) > Clone Git Repositoryウィザードが開く
   - githubアカウントを使用する場合には、ウィザードの2ページ目で設定(参照のみの場合は匿名でOK)
   - ウィザードの3ページ目(Local Destinationページ)で以下を指定;
     - Initial branch: develを選択
     - Imports all eclipse projects after clone finishesをチェック

   正常終了するとeclipseプロジェクト"IMANE[IMANE devel]"が追加される

## プロジェクトのビルド
 - eclipse > IMANEプロジェクト > pom.xmlを選択 > コンテキストメニュー > Run As > Maven install
 - IMANEプロジェクト\target配下にビルド済資材が出力される

## プロジェクトのデバッグ
 - eclipse > IMANEプロジェクト > コンテキストメニュー > Debug as > Debug on Server
   - サーバ選択画面で[eclipseの設定]で作成したtomcatサーバを選択
 - Consoleビューに以下の行が出力されれば起動処理完了  
   > INFO  [workshop]: 2021/04/02 07:57:33: started
  
## 実行環境への配備
 - プロジェクトのビルドで生成された[IMANEプロジェクト]\target\workshop.warを[tomcat格納先]\webapps\にコピー  
