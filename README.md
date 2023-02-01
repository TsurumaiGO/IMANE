# IMANE-PC (Incident management networking exercise) 
### コンピュータ演習形式のサイバーインシデント対応シミュレーション
- IMANE-PCはコンピュータ演習形式のサイバーインシデント対応シミュレーションです。
- 参加者は現実の役職として演習に参加でき、組織間連携を擬似体験できます。
- 結果(=対応フロー)はワークシートとして出力でき、演習の実施後直ぐに評価できます。

### 各組織におけるサイバーインシデント対応演習の構築運用の支援システム
- 演習内容は簡単に構築・カスタマイズ出来ます。
- シナリオデータを共有することで、既存のデータを参考にした演習構築が可能になります。
- 演習結果を共有し、比較することでベストプラクティスの検討が出来ます。

### 参加者は役職を分担して演習に参加し、組織間連携を演習内で擬似体験できる

![IMANE-PCの概要](https://user-images.githubusercontent.com/55830516/83992655-01dd0900-a98c-11ea-94f1-4cb8af3ee356.png)

# Getting Started / スタートガイド
### 前提条件
- サーバ
  - jdk 8以降
  - Tomcat 9.x (Tomcat 10では動作しません)
- クライアント
  - Google Chrome
 
- JDKのインストール
  - OpenJDKをインストール
    - https://openjdk.java.net/

- Tomcatのインストールとworkshop.warの配備
  - Apache Tomcatをインストール
    - http://tomcat.apache.org/
  - apache-tomcat-x.x.xx\webapps内にworkshop.warを配備する

- サーバーの起動
  1. Tomcatを起動
  2. コンソール画面へのアクセス
     -　http://[ホスト名]:8080/workshop/

- 演習開始
  - 「管理者/オブザーバはこちらから」をクリック
  - ログインをクリック
  - 開始ボタンをクリック

- サーバーのシャットダウン
  - Tomcatをシャットダウン

- インストール、実行の手順についてはIMANE-PCサーバー環境構築と演習開始方法のPDFを参照してください。
  - [IMANE-PCサーバー環境構築と演習開始方法](https://docs.google.com/presentation/d/1ekoa2WFWvBIGvYUBVM2yrIjZMWI0hMXu/edit#slide=id.p10https://workshop)

- 操作の流れについてはついてはIMANE-PCの操作説明のPDFを参照してください
  - [IMANE-PCの操作説明](https://workshop)

- 同梱されてる演習シナリオについてはシナリオ概要のPDFを参照してください
  - [シナリオ概要](https://workshop)

# Release Note/リリース情報
2020年5月　ver.1.0 新規

For the versions are available, see the tags on this repository.


# /バグ報告、機能リクエストについて

バグ報告、機能リクエストはテンプレートに従ってissueを発行してください。

# Authors / 著者

TsurumaiGO team
+ tsurumaigo@manage.nitech.ac.jp

# License / ライセンス

This project is licensed under the MIT License - see the LICENSE.md file for details

このプロジェクトは MIT ライセンスの元にライセンスされています。 詳細はLICENSE.mdをご覧ください。

# Acknowledgments / 謝辞

This work was supported by Council for Science, Technology and Innovation (CSTI), Cross-ministerial Strategic Innovation Promotion Program (SIP), “Cyber-Security for Critical Infrastructure” (funding agency: NEDO). 

