#ifndef COMM_H
#define COMM_H

#include <string>
#include <vector>

#include <boost/asio.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/smart_ptr.hpp>
#include <boost/thread/thread.hpp>
#include <boost/thread/mutex.hpp>
#include <boost/thread/lockable_adapter.hpp>

#include <google/protobuf/message.h>

#include "PlatformManager.h"
#include "BlockManager.h"
#include "TaskManager.h"
#include "Logger.h"

using namespace boost::asio;

typedef boost::shared_ptr<ip::tcp::socket> socket_ptr;

namespace blaze {

/*
 * Communicator design for Node Manager
 */
class CommManager
: public boost::basic_lockable_adapter<boost::mutex>
{
public:
  CommManager(
      PlatformManager* _platform,
      std::string address = "127.0.0.1",
      int ip_port = 1027
    ):
    ip_address(address), 
    srv_port(ip_port), 
    platform_manager(_platform)
  { 
    // asynchronously start listening for new connections
    boost::thread t(boost::bind(&CommManager::listen, this));
  }

protected:
  void recv(::google::protobuf::Message&, socket_ptr);

  void send(::google::protobuf::Message&, socket_ptr);

  // pure virtual method called by listen
  virtual void process(socket_ptr) {};

  // reference to platform manager
  PlatformManager *platform_manager;

private:
  void listen();

  int srv_port;
  std::string ip_address;
};

// Manage communication with Application
class AppCommManager : public CommManager 
{
public:
  AppCommManager(
      PlatformManager* _platform,
      std::string address = "127.0.0.1",
      int ip_port = 1027
    ): CommManager(_platform, address, ip_port) {;}
private:
  void process(socket_ptr);
};

class AccReject : public std::logic_error {
public:
  explicit AccReject(const std::string& what_arg):
    std::logic_error(what_arg) {;}
};

class AccFailure : public std::logic_error {
public:
  explicit AccFailure(const std::string& what_arg):
    std::logic_error(what_arg) {;}
};

// Manager communication with GAM
class GAMCommManager : public CommManager 
{
public:
  GAMCommManager(
      PlatformManager* _platform,
      std::string address = "127.0.0.1",
      int ip_port = 1028
    ): CommManager(_platform, address, ip_port) {;}
private:
  void process(socket_ptr);
  std::vector<std::string> last_names;
};


}

#endif
