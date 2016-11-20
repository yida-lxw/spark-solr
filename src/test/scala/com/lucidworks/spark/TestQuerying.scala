package com.lucidworks.spark

import java.util.UUID

import com.lucidworks.spark.util.{ConfigurationConstants, SolrCloudUtil, SolrSupport}
import org.apache.spark.sql.SaveMode.Overwrite

class TestQuerying extends TestSuiteBuilder {

  test("vary queried columns") {
    val collectionName = "testQuerying-" + UUID.randomUUID().toString
    SolrCloudUtil.buildCollection(zkHost, collectionName, null, 2, cloudClient, sc)
    try {
      val csvFileLocation = "src/test/resources/test-data/simple.csv"
      val csvDF = sparkSession.read.format("com.databricks.spark.csv")
        .option("header", "true")
        .option("inferSchema", "true")
        .load(csvFileLocation)
      assert(csvDF.count == 3)

      val solrOpts = Map("zkhost" -> zkHost, "collection" -> collectionName)
      csvDF.write.format("solr").options(solrOpts).mode(Overwrite).save()

      // Explicit commit to make sure all docs are visible
      val solrCloudClient = SolrSupport.getCachedCloudClient(zkHost)
      solrCloudClient.commit(collectionName, true, true)

      val solrDF = sparkSession.read.format("solr").options(solrOpts).load()
      assert(solrDF.count == 3)
      assert(solrDF.schema.fields.length === 6) // id one_txt two_txt three_s _version_ _indexed_at_tdt
      val oneColFirstRow = solrDF.select("one_txt").head()(0) // query for one column
      assert(oneColFirstRow != null)
      val firstRow = solrDF.head.toSeq                        // query for all columns
      assert(firstRow.size === 6)
      firstRow.foreach(col => assert(col != null))            // no missing values

      // Test to make sure sort param is being applied to the query
      {
        val solrDF1 = sparkSession.read.format("solr").options(solrOpts).option(ConfigurationConstants.ARBITRARY_PARAMS_STRING, "sort=id asc").load()
        val rows = solrDF1.collect()
        val idFieldIndex = solrDF1.schema.fieldIndex("id")
        rows.zipWithIndex.foreach{ case(row,i) => {
          assert(row.get(idFieldIndex).equals(Integer.toString(i+1)))
        }}
      }
    } finally {
      SolrCloudUtil.deleteCollection(collectionName, cluster)
    }
  }

  test("vary queried columns with fields option") {
    val collectionName = "testQuerying-" + UUID.randomUUID().toString
    SolrCloudUtil.buildCollection(zkHost, collectionName, null, 2, cloudClient, sc)
    try {
      val csvFileLocation = "src/test/resources/test-data/simple.csv"
      val csvDF = sparkSession.read.format("com.databricks.spark.csv")
        .option("header", "true")
        .option("inferSchema", "true")
        .load(csvFileLocation)
      assert(csvDF.count == 3)

      val solrOpts = Map("zkhost" -> zkHost, "collection" -> collectionName, "solr.params" -> "fl=id,one_txt,two_txt")
      csvDF.write.format("solr").options(solrOpts).mode(Overwrite).save()

      // Explicit commit to make sure all docs are visible
      val solrCloudClient = SolrSupport.getCachedCloudClient(zkHost)
      solrCloudClient.commit(collectionName, true, true)

      val solrDF = sparkSession.read.format("solr").options(solrOpts).load()
      assert(solrDF.count == 3)
      assert(solrDF.schema.fields.length === 3)

      // Query for one column
      val oneColFirstRow = solrDF.select("one_txt").head()(0) // query for one column
      assert(oneColFirstRow != null)

      // Query for all columns
      val firstRow = solrDF.head.toSeq
      assert(firstRow.size === 3)
      firstRow.foreach(col => assert(col != null))            // no missing values
    } finally {
      SolrCloudUtil.deleteCollection(collectionName, cluster)
    }
  }

  test("querying multiple collections") {
    val collection1Name = "testQuerying-" + UUID.randomUUID().toString
    val collection2Name="testQuerying-" + UUID.randomUUID().toString
    SolrCloudUtil.buildCollection(zkHost, collection1Name, null, 2, cloudClient, sc)
    SolrCloudUtil.buildCollection(zkHost, collection2Name, null, 2, cloudClient, sc)
    try {
      val csvFileLocation = "src/test/resources/test-data/simple.csv"
      val csvDF = sparkSession.read.format("com.databricks.spark.csv")
        .option("header", "true")
        .option("inferSchema", "true")
        .load(csvFileLocation)
      assert(csvDF.count == 3)

      val solrOpts_writing1 = Map("zkhost" -> zkHost, "collection" -> collection1Name)
      val solrOpts_writing2 = Map("zkhost" -> zkHost, "collection" -> collection2Name)
      val solrOpts = Map("zkhost" -> zkHost, "collection" -> s"$collection1Name,$collection2Name")


      csvDF.write.format("solr").options(solrOpts_writing1).mode(Overwrite).save()
      csvDF.write.format("solr").options(solrOpts_writing2).mode(Overwrite).save()

      // Explicit commit to make sure all docs are visible
      val solrCloudClient = SolrSupport.getCachedCloudClient(zkHost)
      solrCloudClient.commit(collection1Name, true, true)
      solrCloudClient.commit(collection2Name, true, true)

      val solrDF = sparkSession.read.format("solr").options(solrOpts).load()
      assert(solrDF.count == 6)
      assert(solrDF.schema.fields.length === 6) // id one_txt two_txt three_s _version_ _indexed_at_tdt
      val oneColFirstRow = solrDF.select("one_txt").head()(0) // query for one column
      assert(oneColFirstRow != null)
      val firstRow = solrDF.head.toSeq                        // query for all columns
      assert(firstRow.size === 6)
      firstRow.foreach(col => assert(col != null))            // no missing values
    } finally {
      SolrCloudUtil.deleteCollection(collection1Name, cluster)
      SolrCloudUtil.deleteCollection(collection2Name, cluster)
    }
  }



}
