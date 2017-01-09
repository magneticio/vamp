<img src="http://vamp.io/images/logo.svg" width="250px" />

Vamp is actively developed by [Magnetic.io](http://magnetic.io) but is an open source project. 
We encourage anyone to pitch in with pull requests, issues etc.

Note: We are currently in beta. This means the API will change, features will be added/removed and there will be bugs. Having said that:


*Vamp is split into separate repos and projects, just like actual microservices...* :metal:

1. [Vamp](https://github.com/magneticio/vamp) : the brains of the organization.
2. [Vamp UI](https://github.com/magneticio/vamp-ui) : the Vamp UI.
3. [Vamp Gateway Agent](https://github.com/magneticio/vamp-gateway-agent) : serves as an intermediary agent between supported config stores (ZooKeeper, etcd, Consul) and HAProxy, and routes HAProxy logs to Logstash.
4. [Vamp Workflow Agent](https://github.com/magneticio/vamp-workflow-agent) : simple agent(s) that perform management, maintenance and job related tasks for applications, e.g. auto scaling, analytics aggregation, SLA... 

*We accept bug reports and pull requests on the GitHub repo for each project*.

* If you have a question about how to use Vamp, please [check the Vamp documentation first](http://vamp.io/documentation/).

* If you have a change or new feature in mind, please [suggest it by creating an issue](https://github.com/magneticio/vamp/issues) and tag it with "feature proposal"

When reporting issues, please include the following details:

- Description of issue.
- Vamp info, from the API: `GET <vamp url>/api/v1/info`
- Vamp config, from the API; `GET <vamp url>/api/v1/config`
- Reproduction steps.
- Which orchestrator are you using, DC/OS, Kubernetes, Rancher, etc.
- Log output from Vamp and it's various components. You can get these from the `docker logs` command on the relevant containers.
- Any other information that you might consider important.

Keep Vamping! :heart: 

The Vamp team
