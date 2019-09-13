package springdata

import com.couchbase.client.java.Bucket
import com.couchbase.client.java.Cluster
import com.couchbase.client.java.CouchbaseCluster
import com.couchbase.client.java.cluster.ClusterManager
import org.springframework.data.couchbase.core.CouchbaseTemplate
import spock.lang.Shared
import util.AbstractCouchbaseTest

import java.util.concurrent.TimeUnit

class CouchbaseSpringTemplateTest extends AbstractCouchbaseTest {

  @Shared
  List<CouchbaseTemplate> templates

  @Shared
  Cluster couchbaseCluster

  @Shared
  Cluster memcacheCluster

  def setupSpec() {
    couchbaseCluster = CouchbaseCluster.create(couchbaseEnvironment, Arrays.asList("127.0.0.1"))
    memcacheCluster = CouchbaseCluster.create(memcacheEnvironment, Arrays.asList("127.0.0.1"))
    ClusterManager couchbaseManager = couchbaseCluster.clusterManager(USERNAME, PASSWORD)
    ClusterManager memcacheManager = memcacheCluster.clusterManager(USERNAME, PASSWORD)

    Bucket bucketCouchbase = couchbaseCluster.openBucket(bucketCouchbase.name(), bucketCouchbase.password())
    Bucket bucketMemcache = memcacheCluster.openBucket(bucketMemcache.name(), bucketMemcache.password())

    templates = [new CouchbaseTemplate(couchbaseManager.info(), bucketCouchbase),
                 new CouchbaseTemplate(memcacheManager.info(), bucketMemcache)]
  }

  def cleanupSpec() {
    couchbaseCluster?.disconnect(5, TimeUnit.SECONDS)
    memcacheCluster?.disconnect(5, TimeUnit.SECONDS)
  }

  def "test write #name"() {
    setup:
    def doc = new Doc()

    when:
    template.save(doc)

    then:
    template.findById("1", Doc) != null

    and:
    assertTraces(2) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", name)
      }
      trace(1, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", name)
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }

  def "test remove #name"() {
    setup:
    def doc = new Doc()

    when:
    template.save(doc)
    template.remove(doc)

    then:
    template.findById("1", Doc) == null

    and:
    assertTraces(3) {
      trace(0, 1) {
        assertCouchbaseCall(it, 0, "Bucket.upsert", name)
      }
      trace(1, 1) {
        assertCouchbaseCall(it, 0, "Bucket.remove", name)
      }
      trace(2, 1) {
        assertCouchbaseCall(it, 0, "Bucket.get", name)
      }
    }

    where:
    template << templates
    name = template.couchbaseBucket.name()
  }
}
