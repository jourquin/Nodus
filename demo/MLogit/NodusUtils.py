#!/usr/bin/python3

#
# Copyright (c) 1991-2021 Université catholique de Louvain
#
# <p>Center for Operations Research and Econometrics (CORE)
#
# <p>http://www.uclouvain.be
#
# <p>This file is part of Nodus.
#
# <p>Nodus is free software: you can redistribute it and/or modify it under the terms of the GNU
# General Public License as published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.
#
# <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
# without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU General Public License for more details.
#
# <p>You should have received a copy of the GNU General Public License along with this program. If
# not, see http://www.gnu.org/licenses/.
#

import re
import jaydebeapi

# Returns a connection to a database 
def getNodusJDBCConnection(gateway):
    
    # Get Nodus JDBC connection
    nodusProject = gateway.getNodusProject()
    if not nodusProject.isOpen():
        return None
    
    nodusConn = nodusProject.getMainJDBCConnection()
    
    # Create default converters
    types = gateway.jvm.java.sql.Types
    types_map = {}
    const_re = re.compile('[A-Z][A-Z_]*$')
    for i in dir(types):
        if const_re.match(i):
            types_map[i] = getattr(types, i)
    jaydebeapi._init_types(types_map)

    # Return a connection
    return jaydebeapi.Connection(nodusConn, jaydebeapi._converters)


